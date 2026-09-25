package com.maesamco.judge.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.maesamco.judge.application.command.ExecutionTestCase;
import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService;
import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService.JudgeExecutionPreparation;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JudgeExecutionFacadeTest {

    @Mock
    private JudgeExecutionPersistenceService judgeExecutionPersistenceService;

    @Mock
    private JudgeExecutionPort judgeExecutionPort;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @InjectMocks
    private JudgeExecutionFacade judgeExecutionFacade;

    private ProblemExecutionSpec specWithTestCases(String testCasesJson) {
        return ProblemExecutionSpec.fromPublishedEvent(
                UUID.randomUUID(), UUID.randomUUID(), SubmissionLanguage.JAVA17,
                "starter", testCasesJson, 2000, 256, Instant.now());
    }

    private void stubReadyForExecution(UUID submissionId, JudgeExecutionPreparation preparation) {
        given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                .willReturn(Optional.of(submissionId));
        given(judgeExecutionPersistenceService.loadExecutionPreparation(submissionId))
                .willReturn(preparation);
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("실행 준비가 되면 Judge0에 배치 제출하고 토큰을 저장한다")
        void submitsAndSavesTokens() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new ExecutionTestCase(UUID.randomUUID(), true, "3 5", "8", 1),
                    new ExecutionTestCase(UUID.randomUUID(), false, "1 1", "2", 2)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            stubReadyForExecution(submissionId, preparation);
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1", "token-2"));

            judgeExecutionFacade.execute(submissionId);

            ArgumentCaptor<List<ExecutionTestCase>> testCasesCaptor = ArgumentCaptor.forClass(List.class);
            verify(judgeExecutionPersistenceService).savePendingExecutions(
                    eq(submissionId), testCasesCaptor.capture(), eq(List.of("token-1", "token-2")));
            assertThat(testCasesCaptor.getValue()).hasSize(2);
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
        }

        @Test
        @DisplayName("이미 RUNNING이라 markRunningIfNeeded가 빈 값이면 이후 단계를 호출하지 않는다")
        void skipsWhenAlreadyRunning() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willReturn(Optional.empty());

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService, never()).loadExecutionPreparation(any());
            verify(judgeExecutionPort, never()).submitBatch(any());
            verify(judgeExecutionPersistenceService, never())
                    .savePendingExecutions(any(), any(), any());
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
            verify(judgeExecutionPersistenceService, never()).handleRetryableFailure(any(), any());
        }

        @Test
        @DisplayName("이슈 #350 — RUNNING 전이가 릴레이의 QUEUED 전이와 낙관적 락 충돌해도 최신 상태로 다시 시도해 채점을 이어간다")
        void retriesMarkRunningOnOptimisticLockConflictAndContinues() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new ExecutionTestCase(UUID.randomUUID(), true, "1 2", "3", 1)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willThrow(new ObjectOptimisticLockingFailureException(
                            com.maesamco.judge.domain.entity.Submission.class, submissionId))
                    .willReturn(Optional.of(submissionId));
            given(judgeExecutionPersistenceService.loadExecutionPreparation(submissionId))
                    .willReturn(preparation);
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService, times(2)).markRunningIfNeeded(submissionId);
            verify(judgeExecutionPort).submitBatch(anyList());
        }

        @Test
        @DisplayName("이슈 #350 — 충돌 뒤 다시 읽었을 때 다른 워커가 이미 RUNNING으로 바꿨다면 채점하지 않고 스킵한다")
        void skipsWhenAnotherWorkerAlreadyMarkedRunningAfterConflict() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willThrow(new ObjectOptimisticLockingFailureException(
                            com.maesamco.judge.domain.entity.Submission.class, submissionId))
                    .willReturn(Optional.empty());

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService, times(2)).markRunningIfNeeded(submissionId);
            verify(judgeExecutionPersistenceService, never()).loadExecutionPreparation(any());
            verify(judgeExecutionPort, never()).submitBatch(any());
        }

        @Test
        @DisplayName("이슈 #350 — 낙관적 락 충돌이 계속되면 조용히 종료하지 않고 예외를 전파해 메시지가 재전달되게 한다")
        void propagatesOptimisticLockConflictWhenRetriesExhausted() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willThrow(new ObjectOptimisticLockingFailureException(
                            com.maesamco.judge.domain.entity.Submission.class, submissionId));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> judgeExecutionFacade.execute(submissionId))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);

            verify(judgeExecutionPersistenceService, times(3)).markRunningIfNeeded(submissionId);
            verify(judgeExecutionPersistenceService, never()).loadExecutionPreparation(any());
            verify(judgeExecutionPort, never()).submitBatch(any());
        }

        @Test
        @DisplayName("markRunningIfNeeded이 SUBMISSION_NOT_FOUND를 던지면 RUNNING 전이가 커밋된 적이 없으므로 "
                + "재시도/실패 처리를 하지 않고 조용히 종료한다")
        void doesNothingWhenMarkRunningIfNeededThrowsSubmissionNotFound() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willThrow(new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService, never()).loadExecutionPreparation(any());
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
            verify(judgeExecutionPersistenceService, never()).handleRetryableFailure(any(), any());
            verify(judgeExecutionPort, never()).submitBatch(any());
        }

        @Test
        @DisplayName("markRunningIfNeeded이 예상치 못한 예외를 던져도 상태를 건드린 적이 없으므로 "
                + "재시도/실패 처리를 하지 않고 조용히 종료한다")
        void doesNothingWhenMarkRunningIfNeededThrowsUnexpectedException() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willThrow(new RuntimeException("DB 연결 순단"));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService, never()).loadExecutionPreparation(any());
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
            verify(judgeExecutionPersistenceService, never()).handleRetryableFailure(any(), any());
        }

        @Test
        @DisplayName("loadExecutionPreparation이 PROBLEM_NOT_FOUND를 던지면 재시도 없이 즉시 FAILED 처리한다")
        void marksFailedWhenLoadExecutionPreparationThrowsProblemNotFound() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willReturn(Optional.of(submissionId));
            given(judgeExecutionPersistenceService.loadExecutionPreparation(submissionId))
                    .willThrow(new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            verify(judgeExecutionPersistenceService, never()).handleRetryableFailure(any(), any());
            verify(judgeExecutionPort, never()).submitBatch(any());
        }

        @Test
        @DisplayName("loadExecutionPreparation이 그 외의 BusinessException을 던지면 재시도 경로로 보낸다")
        void handlesAsRetryableWhenLoadExecutionPreparationThrowsOtherBusinessException() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willReturn(Optional.of(submissionId));
            given(judgeExecutionPersistenceService.loadExecutionPreparation(submissionId))
                    .willThrow(new BusinessException(ErrorCode.SUBMISSION_INVALID_STATE_TRANSITION));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).handleRetryableFailure(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
        }

        @Test
        @DisplayName("loadExecutionPreparation이 BusinessException이 아닌 예상치 못한 예외를 던지면 재시도 경로로 보낸다")
        void handlesAsRetryableWhenLoadExecutionPreparationThrowsUnexpectedException() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willReturn(Optional.of(submissionId));
            given(judgeExecutionPersistenceService.loadExecutionPreparation(submissionId))
                    .willThrow(new RuntimeException("DB 연결 순단"));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).handleRetryableFailure(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
        }

        @Test
        @DisplayName("테스트케이스 파싱에 실패하면 예외를 전파하지 않고 INTERNAL_SYSTEM_ERROR로 FAILED 처리한다")
        void marksFailedWhenTestCaseParsingFails() {
            UUID submissionId = UUID.randomUUID();
            ProblemExecutionSpec spec = specWithTestCases("이건-JSON이-아님");
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            stubReadyForExecution(submissionId, preparation);

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            verify(judgeExecutionPort, never()).submitBatch(any());
        }

        @Test
        @DisplayName("테스트케이스가 빈 배열이면 Judge0를 호출하지 않고 INTERNAL_SYSTEM_ERROR로 FAILED 처리한다")
        void marksFailedWhenTestCasesEmpty() {
            UUID submissionId = UUID.randomUUID();
            ProblemExecutionSpec spec = specWithTestCases("[]");
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            stubReadyForExecution(submissionId, preparation);

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            verify(judgeExecutionPort, never()).submitBatch(any());
            verify(judgeExecutionPersistenceService, never())
                    .savePendingExecutions(any(), any(), any());
        }

        @Test
        @DisplayName("Judge0 응답 개수가 요청 개수와 다르면 예외를 전파하지 않고 재시도 처리를 위임한다")
        void handlesRetryableFailureWhenTokenCountMismatches() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new ExecutionTestCase(UUID.randomUUID(), true, "3 5", "8", 1),
                    new ExecutionTestCase(UUID.randomUUID(), false, "1 1", "2", 2)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            stubReadyForExecution(submissionId, preparation);
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).handleRetryableFailure(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
            verify(judgeExecutionPersistenceService, never())
                    .savePendingExecutions(any(), any(), any());
        }

        @Test
        @DisplayName("Judge0 호출 자체가 실패하면 예외를 전파하지 않고 재시도 처리를 위임한다")
        void handlesRetryableFailureWhenJudge0SubmitThrows() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new ExecutionTestCase(UUID.randomUUID(), true, "3 5", "8", 1)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            stubReadyForExecution(submissionId, preparation);
            given(judgeExecutionPort.submitBatch(anyList()))
                    .willThrow(new RuntimeException("Judge0 연결 실패"));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).handleRetryableFailure(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
        }

        @Test
        @DisplayName("토큰 저장이 계속 실패하면 재시도(3회) 소진 후 재시도 큐에 태우지 않고 RESULT_SAVE_FAILURE로 FAILED 처리한다")
        void marksFailedWithoutRetryQueueWhenSavePendingExecutionsKeepsFailing() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new ExecutionTestCase(UUID.randomUUID(), true, "3 5", "8", 1)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            stubReadyForExecution(submissionId, preparation);
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
            doThrow(new RuntimeException("DB 저장 실패"))
                    .when(judgeExecutionPersistenceService)
                    .savePendingExecutions(any(), any(), any());

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService, times(3))
                    .savePendingExecutions(eq(submissionId), any(), eq(List.of("token-1")));
            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.RESULT_SAVE_FAILURE);
            verify(judgeExecutionPersistenceService, never())
                    .handleRetryableFailure(any(), eq(FailureCode.RESULT_SAVE_FAILURE));
        }

        @Test
        @DisplayName("토큰 저장이 재시도 중 성공하면 FAILED 처리하지 않는다")
        void doesNotMarkFailedWhenSavePendingExecutionsSucceedsOnRetry() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new ExecutionTestCase(UUID.randomUUID(), true, "3 5", "8", 1)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            stubReadyForExecution(submissionId, preparation);
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
            doThrow(new RuntimeException("DB 저장 실패 1회차"))
                    .doNothing()
                    .when(judgeExecutionPersistenceService)
                    .savePendingExecutions(any(), any(), any());

            judgeExecutionFacade.execute(submissionId);

            verify(judgeExecutionPersistenceService, times(2))
                    .savePendingExecutions(eq(submissionId), any(), eq(List.of("token-1")));
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
            verify(judgeExecutionPersistenceService, never()).handleRetryableFailure(any(), any());
        }

        @Test
        @DisplayName("FAILED 처리 자체가 실패해도 예외를 전파하지 않는다")
        void doesNotPropagateWhenMarkFailedItselfThrows() {
            UUID submissionId = UUID.randomUUID();
            stubReadyForExecution(submissionId, new JudgeExecutionPreparation(
                    submissionId, "public class Main {}", specWithTestCases("이건-JSON이-아님")));
            doThrow(new RuntimeException("FAILED 전이 자체도 실패"))
                    .when(judgeExecutionPersistenceService)
                    .markFailed(any(), any());

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handleRetryableFailure 자체가 실패해도 예외를 전파하지 않는다")
        void doesNotPropagateWhenHandleRetryableFailureItselfThrows() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.markRunningIfNeeded(submissionId))
                    .willReturn(Optional.of(submissionId));
            given(judgeExecutionPersistenceService.loadExecutionPreparation(submissionId))
                    .willThrow(new RuntimeException("DB 연결 순단"));
            doThrow(new RuntimeException("handleRetryableFailure 자체 실패"))
                    .when(judgeExecutionPersistenceService)
                    .handleRetryableFailure(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).handleRetryableFailure(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
        }
    }
}