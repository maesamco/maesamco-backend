package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import com.maesamco.judge.domain.entity.*;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.domain.repository.SubmissionTestResultRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0Execution;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0ExecutionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JudgeResultPersistenceServiceTest {

    @Mock
    private PendingJudge0ExecutionRepository pendingJudge0ExecutionRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionEventOutboxRepository submissionEventOutboxRepository;

    @Mock
    private ProblemExecutionSpecRepository problemExecutionSpecRepository;

    @Mock
    private JudgeExecutionPersistenceService judgeExecutionPersistenceService;

    private JudgeResultPersistenceService judgeResultPersistenceService;

    @BeforeEach
    void setUp() {
        judgeResultPersistenceService = new JudgeResultPersistenceService(
                pendingJudge0ExecutionRepository,
                submissionTestResultRepository,
                submissionRepository,
                submissionEventOutboxRepository,
                problemExecutionSpecRepository,
                judgeExecutionPersistenceService,
                JsonMapper.builder().build());
    }

    private Submission runningSubmission(UUID submissionId) {
        Submission submission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + submissionId);
        ReflectionTestUtils.setField(submission, "id", submissionId);
        submission.markQueued();
        submission.markRunning();
        return submission;
    }

    private JudgeExecutionResult resultOf(JudgeExecutionStatus status) {
        return new JudgeExecutionResult("token-1", status, null, null, null, null, null);
    }

    @Nested
    @DisplayName("reflectResult")
    class ReflectResult {

        @Test
        @DisplayName("여러 테스트케이스가 실패했을 때, DB 조회 순서와 무관하게 가장 심각한 에러 타입을 최종 result로 반영한다")
        void picksMostSevereErrorTypeRegardlessOfOrder() {
            // given
            UUID submissionId = UUID.randomUUID();
            UUID lastTestCaseId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);

            PendingJudge0Execution lastPending =
                    PendingJudge0Execution.create(submissionId, lastTestCaseId, "token-last", true);

            JudgeExecutionResult lastResult = new JudgeExecutionResult(
                    "token-last", JudgeExecutionStatus.ACCEPTED, "3", null, null, 50L, 1024);

            SubmissionTestResult wrongAnswer = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, false, "-1", SubmissionTestErrorType.WRONG_ANSWER);
            SubmissionTestResult runtimeError = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), false, false, null, SubmissionTestErrorType.RUNTIME_ERROR);

            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdAndPassedFalse(submissionId))
                    .willReturn(List.of(wrongAnswer, runtimeError));
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            // when
            judgeResultPersistenceService.reflectResult(lastPending, lastResult);

            // then
            assertThat(submission.getResult()).isEqualTo(SubmissionResult.RUNTIME_ERROR);
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.COMPLETED);
            verify(submissionRepository).save(submission);

            ArgumentCaptor<SubmissionEventOutbox> outboxCaptor = ArgumentCaptor.forClass(SubmissionEventOutbox.class);
            verify(submissionEventOutboxRepository).save(outboxCaptor.capture());
            assertThat(outboxCaptor.getValue().getPayload()).contains("\"result\":\"RUNTIME_ERROR\"");
        }

        @Test
        @DisplayName("실패 유형이 MEMORY_LIMIT_EXCEEDED 하나뿐이면 다른 유형과 비교 없이 그대로 반영한다")
        void singleFailureTypeIsReflectedAsIs() {
            // given
            UUID submissionId = UUID.randomUUID();
            UUID lastTestCaseId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);

            PendingJudge0Execution lastPending =
                    PendingJudge0Execution.create(submissionId, lastTestCaseId, "token-last", true);
            JudgeExecutionResult lastResult = new JudgeExecutionResult(
                    "token-last", JudgeExecutionStatus.ACCEPTED, "3", null, null, 50L, 1024);

            SubmissionTestResult memoryExceeded = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, false, null, SubmissionTestErrorType.MEMORY_LIMIT_EXCEEDED);

            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdAndPassedFalse(submissionId))
                    .willReturn(List.of(memoryExceeded));
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            // when
            judgeResultPersistenceService.reflectResult(lastPending, lastResult);

            // then
            assertThat(submission.getResult()).isEqualTo(SubmissionResult.MEMORY_LIMIT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("reflectResult — 시스템 실패 상태")
    class SystemFailure {

        @Test
        @DisplayName("INTERNAL_ERROR면 WRONG_ANSWER로 기록하지 않고 markFailed(JUDGE0_RESPONSE_FAILURE)를 호출한다")
        void marksFailedWhenInternalError() {
            UUID submissionId = UUID.randomUUID();
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token-1", true);
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of(pending));

            judgeResultPersistenceService.reflectResult(pending, resultOf(JudgeExecutionStatus.INTERNAL_ERROR));

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
            verify(submissionTestResultRepository, never()).save(any());
            verify(pendingJudge0ExecutionRepository).deleteAll(List.of(pending));
        }

        @Test
        @DisplayName("UNKNOWN이면 WRONG_ANSWER로 기록하지 않고 markFailed(JUDGE0_RESPONSE_FAILURE)를 호출한다")
        void marksFailedWhenUnknown() {
            UUID submissionId = UUID.randomUUID();
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token-2", false);
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of(pending));

            judgeResultPersistenceService.reflectResult(pending, resultOf(JudgeExecutionStatus.UNKNOWN));

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
            verify(submissionTestResultRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 종료 상태라 markFailed가 실패해도 예외를 전파하지 않는다")
        void doesNotPropagateWhenAlreadyTerminal() {
            UUID submissionId = UUID.randomUUID();
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token-3", true);
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of(pending));
            willThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND))
                    .given(judgeExecutionPersistenceService)
                    .markFailed(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);

            judgeResultPersistenceService.reflectResult(pending, resultOf(JudgeExecutionStatus.INTERNAL_ERROR));

            // 예외가 여기까지 전파되지 않고 조용히 끝나면 성공
        }
    }
}