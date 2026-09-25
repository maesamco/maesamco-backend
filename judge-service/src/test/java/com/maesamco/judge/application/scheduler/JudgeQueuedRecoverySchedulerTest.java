package com.maesamco.judge.application.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.facade.JudgeExecutionFacade;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("JudgeQueuedRecoveryScheduler (이슈 #350)")
class JudgeQueuedRecoverySchedulerTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private JudgeExecutionFacade judgeExecutionFacade;

    @InjectMocks
    private JudgeQueuedRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "staleSeconds", 60L);
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);
        ReflectionTestUtils.setField(scheduler, "maxRunSeconds", 20L);
    }

    private Submission queuedSubmission(UUID id) {
        Submission submission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
        submission.markQueued();
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }

    @Test
    @DisplayName("정체된 QUEUED 제출마다 채점을 다시 실행한다")
    void reExecutesEveryStalledQueuedSubmission() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        given(submissionRepository.findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
                eq(SubmissionStatus.QUEUED), any(Instant.class), any(Pageable.class)))
                .willReturn(List.of(queuedSubmission(first), queuedSubmission(second)));

        scheduler.recoverStalledQueuedSubmissions();

        verify(judgeExecutionFacade).execute(first);
        verify(judgeExecutionFacade).execute(second);
    }

    @Test
    @DisplayName("설정한 정체 기준 시간(초)보다 오래된 제출만 조회하고 batchSize만큼 가져온다")
    void queriesOnlySubmissionsOlderThanStaleThreshold() {
        given(submissionRepository.findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
                any(), any(Instant.class), any(Pageable.class))).willReturn(List.of());
        Instant before = Instant.now();

        scheduler.recoverStalledQueuedSubmissions();

        ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
        verify(submissionRepository).findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
                eq(SubmissionStatus.QUEUED), threshold.capture(), eq(PageRequest.of(0, 50)));
        // threshold = 호출 시각 - 60초
        assertThat(threshold.getValue()).isBetween(
                before.minusSeconds(60), Instant.now().minusSeconds(60));
    }

    @Test
    @DisplayName("정체된 제출이 없으면 아무것도 실행하지 않는다")
    void doesNothingWhenNothingIsStalled() {
        given(submissionRepository.findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
                any(), any(Instant.class), any(Pageable.class))).willReturn(List.of());

        scheduler.recoverStalledQueuedSubmissions();

        verify(judgeExecutionFacade, never()).execute(any());
    }

    @Test
    @DisplayName("한 주기 시간 상한을 넘기면 남은 제출은 다음 주기로 넘기되 최소 1건은 처리한다")
    void stopsAfterTimeBudgetButAlwaysProcessesAtLeastOne() {
        ReflectionTestUtils.setField(scheduler, "maxRunSeconds", 0L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        given(submissionRepository.findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
                any(), any(Instant.class), any(Pageable.class)))
                .willReturn(List.of(queuedSubmission(first), queuedSubmission(second)));

        scheduler.recoverStalledQueuedSubmissions();

        verify(judgeExecutionFacade).execute(first);
        verify(judgeExecutionFacade, never()).execute(second);
    }

    @Test
    @DisplayName("한 제출이 낙관적 락 충돌이나 예외로 실패해도 다음 제출을 계속 복구한다")
    void continuesWithNextSubmissionWhenOneFails() {
        UUID conflicted = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        given(submissionRepository.findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
                any(), any(Instant.class), any(Pageable.class)))
                .willReturn(List.of(
                        queuedSubmission(conflicted), queuedSubmission(broken), queuedSubmission(healthy)));
        doThrow(new ObjectOptimisticLockingFailureException(Submission.class, conflicted))
                .when(judgeExecutionFacade).execute(conflicted);
        doThrow(new IllegalStateException("예상치 못한 오류")).when(judgeExecutionFacade).execute(broken);

        assertThatCode(() -> scheduler.recoverStalledQueuedSubmissions()).doesNotThrowAnyException();

        verify(judgeExecutionFacade).execute(healthy);
    }
}
