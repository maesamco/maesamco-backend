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

import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
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
                    submissionId, UUID.randomUUID(), true, false, "-1", SubmissionTestErrorType.WRONG_ANSWER, 120, 2048);
            SubmissionTestResult runtimeError = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), false, false, null, SubmissionTestErrorType.RUNTIME_ERROR, 80, 4096);

            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                    .willReturn(List.of(wrongAnswer, runtimeError));
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            // when
            judgeResultPersistenceService.reflectResult(lastPending, lastResult);

            // then
            assertThat(submission.getResult()).isEqualTo(SubmissionResult.RUNTIME_ERROR);
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.COMPLETED);
            assertThat(submission.getExecutionTimeMs()).isEqualTo(120);
            assertThat(submission.getMemoryUsedKb()).isEqualTo(4096);
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
                    submissionId, UUID.randomUUID(), true, false, null, SubmissionTestErrorType.MEMORY_LIMIT_EXCEEDED, 90, 3072);

            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                    .willReturn(List.of(memoryExceeded));
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            // when
            judgeResultPersistenceService.reflectResult(lastPending, lastResult);

            // then
            assertThat(submission.getResult()).isEqualTo(SubmissionResult.MEMORY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("완료 시 발행되는 SubmissionJudged payload에 problemVersionId/attemptNo/judgedAt이 모두 포함된다")
        void publishesPayloadWithAllFields() {
            // given
            UUID submissionId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);

            PendingJudge0Execution lastPending =
                    PendingJudge0Execution.create(submissionId, UUID.randomUUID(), "token-last", true);
            JudgeExecutionResult lastResult = new JudgeExecutionResult(
                    "token-last", JudgeExecutionStatus.ACCEPTED, "3", null, null, 50L, 1024);

            SubmissionTestResult passed = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, true, "3", null, 150, 2048);

            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                    .willReturn(List.of(passed));
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            // when
            judgeResultPersistenceService.reflectResult(lastPending, lastResult);

            // then
            ArgumentCaptor<SubmissionEventOutbox> outboxCaptor = ArgumentCaptor.forClass(SubmissionEventOutbox.class);
            verify(submissionEventOutboxRepository).save(outboxCaptor.capture());
            String payload = outboxCaptor.getValue().getPayload();

            JsonNode json = JsonMapper.builder().build().readTree(payload);
            assertThat(json.get("problemVersionId").asText()).isEqualTo(submission.getProblemVersionId().toString());
            assertThat(json.get("attemptNo").asInt()).isEqualTo(submission.getAttemptNo());
            assertThat(Instant.parse(json.get("judgedAt").asText())).isEqualTo(submission.getJudgedAt());
        }
    }

    @Nested
    @DisplayName("reflectResult — 시스템 실패 상태")
    class SystemFailure {

        @Test
        @DisplayName("INTERNAL_ERROR면 WRONG_ANSWER로 기록하지 않고 markFailed(JUDGE0_RESPONSE_FAILURE)를 호출한다")
        void marksFailedWhenInternalError() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token-1", true);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
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
            Submission submission = runningSubmission(submissionId);
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token-2", false);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
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
            Submission submission = runningSubmission(submissionId);
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token-3", true);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of(pending));
            willThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND))
                    .given(judgeExecutionPersistenceService)
                    .markFailed(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);

            judgeResultPersistenceService.reflectResult(pending, resultOf(JudgeExecutionStatus.INTERNAL_ERROR));
        }
    }

    @Nested
    @DisplayName("reflectResult — 이미 종료된 제출")
    class AlreadyTerminal {

        @Test
        @DisplayName("이미 COMPLETED인 제출에 결과가 늦게 도착하면 반영하지 않고 pending만 정리한다")
        void skipsWhenAlreadyCompletedByCompileError() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);
            submission.markCompleted(SubmissionResult.CORRECT, 100, 1024);
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token-late", true);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            judgeResultPersistenceService.reflectResult(pending, resultOf(JudgeExecutionStatus.COMPILE_ERROR));

            verify(pendingJudge0ExecutionRepository).delete(pending);
            verify(submissionRepository, never()).save(any());
            verify(submissionEventOutboxRepository, never()).save(any());
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
        }

        @Test
        @DisplayName("이미 FAILED인 제출에 결과가 늦게 도착하면 반영하지 않고 pending만 정리한다")
        void skipsWhenAlreadyFailed() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);
            submission.markFailed(FailureCode.JUDGE0_RESPONSE_FAILURE);
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token-late", true);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            judgeResultPersistenceService.reflectResult(pending, resultOf(JudgeExecutionStatus.ACCEPTED));

            verify(pendingJudge0ExecutionRepository).delete(pending);
            verify(submissionTestResultRepository, never()).save(any());
            verify(submissionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("reflectResult — 채점 기준 조회 실패")
    class MissingSpec {

        @Test
        @DisplayName("ProblemExecutionSpec을 찾을 수 없으면 재시도 없이 즉시 FAILED 처리한다")
        void marksFailedImmediatelyWhenSpecNotFound() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);
            PendingJudge0Execution pending = PendingJudge0Execution.create(
                    submissionId, UUID.randomUUID(), "token", true);
            JudgeExecutionResult result = new JudgeExecutionResult(
                    "token", JudgeExecutionStatus.WRONG_ANSWER, "output", null, null, 100L, 1024);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(problemExecutionSpecRepository.findByProblemIdAndProblemVersionId(any(), any()))
                    .willReturn(Optional.empty());
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of(pending));

            judgeResultPersistenceService.reflectResult(pending, result);

            verify(pendingJudge0ExecutionRepository).deleteAll(List.of(pending));
            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            verify(submissionTestResultRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("reflectResult — 제출 단위 실행시간/메모리 집계")
    class ExecutionMetrics {

        @Test
        @DisplayName("테스트케이스별 실행시간이 다르면 그중 최댓값을 제출의 실행시간으로 저장한다")
        void storesMaxExecutionTimeAcrossTestCases() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);
            PendingJudge0Execution lastPending =
                    PendingJudge0Execution.create(submissionId, UUID.randomUUID(), "token-last", true);
            JudgeExecutionResult lastResult = new JudgeExecutionResult(
                    "token-last", JudgeExecutionStatus.ACCEPTED, "3", null, null, 50L, 1024);

            SubmissionTestResult fast = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, true, "3", null, 50, 1024);
            SubmissionTestResult slow = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, true, "3", null, 480, 2048);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                    .willReturn(List.of(fast, slow));

            judgeResultPersistenceService.reflectResult(lastPending, lastResult);

            assertThat(submission.getExecutionTimeMs()).isEqualTo(480);
        }

        @Test
        @DisplayName("테스트케이스별 메모리 사용량이 다르면 그중 최댓값을 제출의 메모리 사용량으로 저장한다")
        void storesMaxMemoryUsedAcrossTestCases() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = runningSubmission(submissionId);
            PendingJudge0Execution lastPending =
                    PendingJudge0Execution.create(submissionId, UUID.randomUUID(), "token-last", true);
            JudgeExecutionResult lastResult = new JudgeExecutionResult(
                    "token-last", JudgeExecutionStatus.ACCEPTED, "3", null, null, 50L, 1024);

            SubmissionTestResult light = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, true, "3", null, 100, 1024);
            SubmissionTestResult heavy = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, true, "3", null, 100, 8192);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                    .willReturn(List.of(light, heavy));

            judgeResultPersistenceService.reflectResult(lastPending, lastResult);

            assertThat(submission.getMemoryUsedKb()).isEqualTo(8192);
        }

        @Test
        @DisplayName("결과 처리 순서가 달라도(어떤 결과가 마지막에 도착해 allDone을 트리거하든) 동일한 제출 지표가 저장된다")
        void storesSameMetricsRegardlessOfProcessingOrder() {
            UUID submissionIdA = UUID.randomUUID();
            Submission submissionA = runningSubmission(submissionIdA);
            UUID submissionIdB = UUID.randomUUID();
            Submission submissionB = runningSubmission(submissionIdB);

            // 시나리오 A — 마지막에 도착해 트리거하는 건이 실행시간이 긴 쪽(Y)
            SubmissionTestResult x1 = SubmissionTestResult.create(
                    submissionIdA, UUID.randomUUID(), true, true, "3", null, 100, 2048);
            SubmissionTestResult y1 = SubmissionTestResult.create(
                    submissionIdA, UUID.randomUUID(), true, true, "3", null, 300, 1024);
            PendingJudge0Execution triggerByY =
                    PendingJudge0Execution.create(submissionIdA, UUID.randomUUID(), "token-y", true);
            JudgeExecutionResult resultOfY = new JudgeExecutionResult(
                    "token-y", JudgeExecutionStatus.ACCEPTED, "3", null, null, 300L, 1024);

            given(submissionRepository.findById(submissionIdA)).willReturn(Optional.of(submissionA));
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionIdA))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionIdA))
                    .willReturn(List.of(x1, y1));

            judgeResultPersistenceService.reflectResult(triggerByY, resultOfY);

            // 시나리오 B — 동일한 결과 집합이지만, 마지막에 도착해 트리거하는 건이 실행시간이 짧은 쪽(X)
            SubmissionTestResult x2 = SubmissionTestResult.create(
                    submissionIdB, UUID.randomUUID(), true, true, "3", null, 100, 2048);
            SubmissionTestResult y2 = SubmissionTestResult.create(
                    submissionIdB, UUID.randomUUID(), true, true, "3", null, 300, 1024);
            PendingJudge0Execution triggerByX =
                    PendingJudge0Execution.create(submissionIdB, UUID.randomUUID(), "token-x", true);
            JudgeExecutionResult resultOfX = new JudgeExecutionResult(
                    "token-x", JudgeExecutionStatus.ACCEPTED, "3", null, null, 100L, 2048);

            given(submissionRepository.findById(submissionIdB)).willReturn(Optional.of(submissionB));
            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionIdB))
                    .willReturn(List.of());
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionIdB))
                    .willReturn(List.of(x2, y2));

            judgeResultPersistenceService.reflectResult(triggerByX, resultOfX);

            // then — 트리거가 된 "마지막 결과"가 무엇이든, 저장된 집계 지표는 동일해야 함
            assertThat(submissionA.getExecutionTimeMs()).isEqualTo(submissionB.getExecutionTimeMs());
            assertThat(submissionA.getMemoryUsedKb()).isEqualTo(submissionB.getMemoryUsedKb());
            assertThat(submissionA.getExecutionTimeMs()).isEqualTo(300);
            assertThat(submissionA.getMemoryUsedKb()).isEqualTo(2048);
        }
    }
}