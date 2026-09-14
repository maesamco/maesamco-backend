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
import org.springframework.data.domain.Pageable;
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
    }

    private Submission retryWaitSubmission(UUID id) {
        Submission submission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }

    @Nested
    @DisplayName("retryPendingSubmissions")
    class RetryPendingSubmissions {

        @Test
        @DisplayName("RETRY_WAIT 상태인 제출들을 조회해 각각 execute를 재호출한다")
        void callsExecuteForEachRetryTarget() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAsc(
                    eq(SubmissionStatus.RETRY_WAIT), any(Pageable.class)))
                    .willReturn(List.of(retryWaitSubmission(id1), retryWaitSubmission(id2)));

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade).execute(id1);
            verify(judgeExecutionFacade).execute(id2);
        }

        @Test
        @DisplayName("재시도 대상이 없으면 execute를 호출하지 않는다")
        void doesNothingWhenNoRetryTargets() {
            given(submissionRepository.findByStatusOrderBySubmittedAtAsc(
                    eq(SubmissionStatus.RETRY_WAIT), any(Pageable.class)))
                    .willReturn(List.of());

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade, never()).execute(any());
        }

        @Test
        @DisplayName("한 건에서 낙관적 락 충돌이 나도 나머지 건은 계속 처리한다")
        void continuesAfterOptimisticLockingFailure() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAsc(
                    eq(SubmissionStatus.RETRY_WAIT), any(Pageable.class)))
                    .willReturn(List.of(retryWaitSubmission(id1), retryWaitSubmission(id2)));
            doThrow(new ObjectOptimisticLockingFailureException(Submission.class, id1))
                    .when(judgeExecutionFacade).execute(id1);

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade).execute(id1);
            verify(judgeExecutionFacade).execute(id2); // 첫 건이 실패해도 두 번째 건은 처리돼야 함
        }

        @Test
        @DisplayName("한 건에서 예상치 못한 예외가 나도 나머지 건은 계속 처리한다")
        void continuesAfterUnexpectedException() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            given(submissionRepository.findByStatusOrderBySubmittedAtAsc(
                    eq(SubmissionStatus.RETRY_WAIT), any(Pageable.class)))
                    .willReturn(List.of(retryWaitSubmission(id1), retryWaitSubmission(id2)));
            doThrow(new IllegalStateException("prepareForExecution 실패"))
                    .when(judgeExecutionFacade).execute(id1);

            judgeRetryScheduler.retryPendingSubmissions();

            verify(judgeExecutionFacade).execute(id1);
            verify(judgeExecutionFacade).execute(id2);
        }
    }
}