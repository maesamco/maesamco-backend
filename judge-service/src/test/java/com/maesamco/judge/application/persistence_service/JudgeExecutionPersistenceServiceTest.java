package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.command.ExecutionTestCase;
import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService.JudgeExecutionPreparation;
import com.maesamco.judge.domain.entity.*;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0Execution;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0ExecutionRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class JudgeExecutionPersistenceServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ProblemExecutionSpecRepository problemExecutionSpecRepository;

    @Mock
    private PendingJudge0ExecutionRepository pendingJudge0ExecutionRepository;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private JudgeExecutionPersistenceService judgeExecutionPersistenceService;

    @BeforeEach
    void setUp() {
        judgeExecutionPersistenceService = new JudgeExecutionPersistenceService(
                submissionRepository, problemExecutionSpecRepository, pendingJudge0ExecutionRepository, meterRegistry);
        ReflectionTestUtils.setField(judgeExecutionPersistenceService, "maxRetryCount", 3);
    }

    /**
     * registerAfterCommitMetric()이 TransactionSynchronizationManager를 쓰기 때문에,
     * 실제 트랜잭션 없이 호출하면 "synchronization is not active" 예외가 난다.
     * 여기서 동기화를 직접 열고, action 실행 후 등록된 afterCommit 콜백을 수동으로
     * 트리거해서 실제 커밋이 성공한 상황을 흉내낸다.
     */
    private void runAsCommittedTransaction(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Submission queuedSubmission() {
        Submission submission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, "idem-key-" + UUID.randomUUID());
        submission.markQueued();
        return submission;
    }

    private ProblemExecutionSpec specFor(Submission submission) {
        return ProblemExecutionSpec.fromPublishedEvent(
                submission.getProblemId(), submission.getProblemVersionId(),
                SubmissionLanguage.JAVA17, "starter", "[]", 2000, 256, Instant.now());
    }

    @Nested
    @DisplayName("prepareForExecution")
    class PrepareForExecution {

        @Test
        @DisplayName("정상 흐름에서는 Submission을 RUNNING으로 전이시키고 실행 준비 정보를 반환한다")
        void marksRunningAndReturnsPreparation() {
            Submission submission = queuedSubmission();
            ProblemExecutionSpec spec = specFor(submission);
            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));
            given(problemExecutionSpecRepository.findByProblemIdAndProblemVersionId(
                    submission.getProblemId(), submission.getProblemVersionId())).willReturn(Optional.of(spec));

            Optional<JudgeExecutionPreparation> result =
                    judgeExecutionPersistenceService.prepareForExecution(submission.getId());

            assertThat(result).isPresent();
            assertThat(result.get().submissionId()).isEqualTo(submission.getId());
            assertThat(result.get().code()).isEqualTo(submission.getCode());
            assertThat(result.get().spec()).isEqualTo(spec);
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
        }

        @Test
        @DisplayName("이미 RUNNING 상태인 중복 이벤트면 빈 값을 반환하고 실행 명세를 조회하지 않는다")
        void returnsEmptyWhenAlreadyRunning() {
            Submission submission = queuedSubmission();
            submission.markRunning();
            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));

            Optional<JudgeExecutionPreparation> result =
                    judgeExecutionPersistenceService.prepareForExecution(submission.getId());

            assertThat(result).isEmpty();
            verify(problemExecutionSpecRepository, never()).findByProblemIdAndProblemVersionId(any(), any());
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
        }

        @Test
        @DisplayName("존재하지 않는 제출이면 SUBMISSION_NOT_FOUND 예외를 던진다")
        void throwsWhenSubmissionNotFound() {
            UUID submissionId = UUID.randomUUID();
            given(submissionRepository.findById(submissionId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBMISSION_NOT_FOUND);
        }

        @Test
        @DisplayName("실행 명세가 없으면 PROBLEM_NOT_FOUND 예외를 던진다")
        void throwsWhenProblemExecutionSpecNotFound() {
            Submission submission = queuedSubmission();
            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));
            given(problemExecutionSpecRepository.findByProblemIdAndProblemVersionId(
                    submission.getProblemId(), submission.getProblemVersionId())).willReturn(Optional.empty());

            assertThatThrownBy(() -> judgeExecutionPersistenceService.prepareForExecution(submission.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROBLEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("savePendingExecutions")
    class SavePendingExecutions {

        @Test
        @DisplayName("토큰별로 PendingJudge0Execution을 저장한다")
        void savesAllTokens() {
            UUID submissionId = UUID.randomUUID();
            List<ExecutionTestCase> testCases = List.of(
                    new ExecutionTestCase(UUID.randomUUID(), true, "3 5", "8", 1),
                    new ExecutionTestCase(UUID.randomUUID(), false, "1 1", "2", 2)
            );

            judgeExecutionPersistenceService.savePendingExecutions(
                    submissionId, testCases, List.of("token-1", "token-2"));

            ArgumentCaptor<List<PendingJudge0Execution>> captor = ArgumentCaptor.forClass(List.class);
            verify(pendingJudge0ExecutionRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
        }

        @Test
        @DisplayName("토큰이 null인 테스트케이스는 저장하지 않고 건너뛴다")
        void skipsNullTokens() {
            UUID submissionId = UUID.randomUUID();
            List<ExecutionTestCase> testCases = List.of(
                    new ExecutionTestCase(UUID.randomUUID(), true, "3 5", "8", 1),
                    new ExecutionTestCase(UUID.randomUUID(), true, "1 1", "2", 2)
            );

            judgeExecutionPersistenceService.savePendingExecutions(
                    submissionId, testCases, Arrays.asList("token-1", null));

            ArgumentCaptor<List<PendingJudge0Execution>> captor = ArgumentCaptor.forClass(List.class);
            verify(pendingJudge0ExecutionRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailed {

        @Test
        @DisplayName("RUNNING 상태의 제출을 FAILED로 전이시키고 failureCode를 기록한다")
        void marksSubmissionFailed() {
            Submission submission = queuedSubmission();
            submission.markRunning();
            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));

            judgeExecutionPersistenceService.markFailed(submission.getId(), FailureCode.JUDGE0_RESPONSE_FAILURE);

            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(submission.getFailureCode()).isEqualTo(FailureCode.JUDGE0_RESPONSE_FAILURE);
        }

        @Test
        @DisplayName("존재하지 않는 제출이면 SUBMISSION_NOT_FOUND 예외를 던진다")
        void throwsWhenSubmissionNotFound() {
            UUID submissionId = UUID.randomUUID();
            given(submissionRepository.findById(submissionId)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    judgeExecutionPersistenceService.markFailed(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBMISSION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("handleRetryableFailure")
    class HandleRetryableFailure {

        @Test
        @DisplayName("재시도 횟수가 남아있으면 RETRY_WAIT로 전이시키고, 커밋 후 카운터를 증가시킨다")
        void marksRetryWaitWhenRetriesRemain() {
            Submission submission = queuedSubmission();
            submission.markRunning();
            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));

            runAsCommittedTransaction(() -> judgeExecutionPersistenceService.handleRetryableFailure(
                    submission.getId(), FailureCode.JUDGE0_RESPONSE_FAILURE));

            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.RETRY_WAIT);
            assertThat(submission.getRetryCount()).isEqualTo(1);
            assertThat(meterRegistry.counter("judge.submission.retry.wait", "failureCode", "JUDGE0_RESPONSE_FAILURE").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("재시도 횟수가 소진되면 FAILED로 전이시키고, 커밋 후 카운터를 증가시킨다")
        void marksFailedWhenRetriesExhausted() {
            Submission submission = queuedSubmission();
            submission.markRunning();
            submission.markRetryWait(); submission.markRunning(); // retryCount=1
            submission.markRetryWait(); submission.markRunning(); // retryCount=2
            submission.markRetryWait(); submission.markRunning(); // retryCount=3 (MAX)
            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));

            runAsCommittedTransaction(() -> judgeExecutionPersistenceService.handleRetryableFailure(
                    submission.getId(), FailureCode.RESULT_SAVE_FAILURE));

            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(submission.getFailureCode()).isEqualTo(FailureCode.RESULT_SAVE_FAILURE);
            assertThat(meterRegistry.counter("judge.submission.retry.exhausted", "failureCode", "RESULT_SAVE_FAILURE").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("커밋되지 않으면(트랜잭션 실패) 카운터가 증가하지 않는다")
        void doesNotIncrementCounterWhenNotCommitted() {
            Submission submission = queuedSubmission();
            submission.markRunning();
            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));

            TransactionSynchronizationManager.initSynchronization();
            try {
                judgeExecutionPersistenceService.handleRetryableFailure(
                        submission.getId(), FailureCode.JUDGE0_RESPONSE_FAILURE);
                // afterCommit()을 트리거하지 않고 그냥 클리어 — 롤백된 상황을 흉내냄
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }

            assertThat(meterRegistry.counter("judge.submission.retry.wait", "failureCode", "JUDGE0_RESPONSE_FAILURE").count())
                    .isEqualTo(0.0);
        }

        @Test
        @DisplayName("존재하지 않는 제출이면 SUBMISSION_NOT_FOUND 예외를 던진다")
        void throwsWhenSubmissionNotFound() {
            UUID submissionId = UUID.randomUUID();
            given(submissionRepository.findById(submissionId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> judgeExecutionPersistenceService.handleRetryableFailure(
                    submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBMISSION_NOT_FOUND);
        }
    }
}