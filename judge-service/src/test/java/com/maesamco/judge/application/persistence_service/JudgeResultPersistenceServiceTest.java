package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import com.maesamco.judge.domain.entity.*;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.domain.repository.SubmissionTestResultRepository;
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
import org.mockito.InjectMocks;
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

    private JudgeResultPersistenceService judgeResultPersistenceService;

    @BeforeEach
    void setUp() {
        judgeResultPersistenceService = new JudgeResultPersistenceService(
                pendingJudge0ExecutionRepository,
                submissionTestResultRepository,
                submissionRepository,
                submissionEventOutboxRepository,
                problemExecutionSpecRepository,
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

            // 마지막 테스트케이스는 통과 — 이 호출로 allDone이 되면서 최종 판정이 일어남
            JudgeExecutionResult lastResult = new JudgeExecutionResult(
                    "token-last", JudgeExecutionStatus.ACCEPTED, "3", null, null, 50L, 1024);

            // 앞서 저장되어 있던 실패 결과 2건 — WRONG_ANSWER가 리스트 앞쪽에 오도록 순서를 일부러 그렇게 둠
            SubmissionTestResult wrongAnswer = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, false, "-1", SubmissionTestErrorType.WRONG_ANSWER);
            SubmissionTestResult runtimeError = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), false, false, null, SubmissionTestErrorType.RUNTIME_ERROR);

            given(pendingJudge0ExecutionRepository.findAllBySubmissionId(submissionId))
                    .willReturn(List.of()); // 마지막 건 삭제 후 남은 pending 없음 -> allDone
            given(submissionTestResultRepository.findBySubmissionIdAndPassedFalse(submissionId))
                    .willReturn(List.of(wrongAnswer, runtimeError)); // WRONG_ANSWER가 먼저 나옴
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            // when
            judgeResultPersistenceService.reflectResult(lastPending, lastResult);

            // then — 리스트 순서상 먼저 나온 WRONG_ANSWER가 아니라, 더 심각한 RUNTIME_ERROR가 최종 반영돼야 한다
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
}