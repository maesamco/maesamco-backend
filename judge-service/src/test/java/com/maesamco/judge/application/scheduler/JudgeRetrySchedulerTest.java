package com.maesamco.judge.application.scheduler;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JudgeRetrySchedulerTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private JudgeExecutionFacade judgeExecutionFacade;

    @InjectMocks
    private JudgeRetryScheduler judgeRetryScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(judgeRetryScheduler, "batchSize", 100);
        ReflectionTestUtils.setField(judgeRetryScheduler, "maxRetryAttempts", 3);
    }

    private Submission retryWaitSubmission(UUID id, int retryCount, Instant updatedAt) {
        Submission submission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
        submission.markQueued();
        submission.markRunning();
        submission.markRetryWait();
        ReflectionTestUtils.setField(submission, "id", id);
        ReflectionTestUtils.setField(submission, "retryCount", retryCount);
        ReflectionTestUtils.setField(submission, "updatedAt", updatedAt);
        return submission;
    }

    private Submission retryWaitSubmission(UUID id) {
        return retryWaitSubmission(id, 1, Instant.now().minusSeconds(3600));
    }

    @Nested
    @DisplayName("retryPendingSubmissions")
    class RetryPendingSubmissions {

        @Test
        @DisplayName("RETRY_WAIT 상태인 제출들을 조회해 각각 execute를 재호출한다")
        void callsExecuteForEachRetryTarget() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
                    eq(SubmissionStatus.RETRY_WAIT.name()), eq(100)))
                    .willReturn(List.of(retryWaitSubmission(id1), retryWaitSubmission(id2)));

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade).execute(id1);
            verify(judgeExecutionFacade).execute(id2);
        }

        @Test
        @DisplayName("재시도 대상이 없으면 execute를 호출하지 않는다")
        void doesNothingWhenNoRetryTargets() {
            given(submissionRepository.findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
                    eq(SubmissionStatus.RETRY_WAIT.name()), eq(100)))
                    .willReturn(List.of());

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade, never()).execute(any());
        }

        @Test
        @DisplayName("한 건에서 낙관적 락 충돌이 나도 나머지 건은 계속 처리한다")
        void continuesAfterOptimisticLockingFailure() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
                    eq(SubmissionStatus.RETRY_WAIT.name()), eq(100)))
                    .willReturn(List.of(retryWaitSubmission(id1), retryWaitSubmission(id2)));
            doThrow(new ObjectOptimisticLockingFailureException(Submission.class, id1))
                    .when(judgeExecutionFacade).execute(id1);

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade).execute(id1);
            verify(judgeExecutionFacade).execute(id2);
        }

        @Test
        @DisplayName("한 건에서 예상치 못한 예외가 나도 나머지 건은 계속 처리한다")
        void continuesAfterUnexpectedException() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
                    eq(SubmissionStatus.RETRY_WAIT.name()), eq(100)))
                    .willReturn(List.of(retryWaitSubmission(id1), retryWaitSubmission(id2)));
            doThrow(new IllegalStateException("prepareForExecution 실패"))
                    .when(judgeExecutionFacade).execute(id1);

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade).execute(id1);
            verify(judgeExecutionFacade).execute(id2);
        }

        @Test
        @DisplayName("백오프 시간이 아직 안 지난 제출은 재시도하지 않고 스킵한다")
        void skipsWhenBackoffNotElapsed() {
            UUID id = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
                    eq(SubmissionStatus.RETRY_WAIT.name()), eq(100)))
                    .willReturn(List.of(retryWaitSubmission(id, 1, Instant.now())));

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade, never()).execute(any());
        }

        @Test
        @DisplayName("백오프 시간이 지난 제출은 재시도한다")
        void retriesWhenBackoffElapsed() {
            UUID id = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
                    eq(SubmissionStatus.RETRY_WAIT.name()), eq(100)))
                    .willReturn(List.of(retryWaitSubmission(id, 1, Instant.now().minusSeconds(15))));

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade).execute(id);
        }

        @Test
        @DisplayName("retryCount가 늘수록 백오프도 지수적으로 늘어난다")
        void backoffGrowsExponentiallyWithRetryCount() {
            UUID id = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
                    eq(SubmissionStatus.RETRY_WAIT.name()), eq(100)))
                    .willReturn(List.of(retryWaitSubmission(id, 2, Instant.now().minusSeconds(15))));

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade, never()).execute(any());
        }
    }
}