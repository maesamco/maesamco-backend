package com.maesamco.judge.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService;
import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService.JudgeExecutionPreparation;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent.TestCaseItem;
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

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("실행 준비가 되면 Judge0에 배치 제출하고 토큰을 저장한다")
        void submitsAndSavesTokens() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new TestCaseItem(UUID.randomUUID(), true, "3 5", "8", 1),
                    new TestCaseItem(UUID.randomUUID(), false, "1 1", "2", 2)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            given(judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .willReturn(Optional.of(preparation));
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1", "token-2"));

            judgeExecutionFacade.execute(submissionId);

            ArgumentCaptor<List<TestCaseItem>> testCasesCaptor = ArgumentCaptor.forClass(List.class);
            verify(judgeExecutionPersistenceService).savePendingExecutions(
                    eq(submissionId), testCasesCaptor.capture(), eq(List.of("token-1", "token-2")));
            assertThat(testCasesCaptor.getValue()).hasSize(2);
            verify(judgeExecutionPersistenceService, never()).markFailed(any(), any());
        }

        @Test
        @DisplayName("이미 RUNNING이라 준비 단계가 빈 값이면 Judge0를 호출하지 않는다")
        void skipsWhenPreparationEmpty() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .willReturn(Optional.empty());

            judgeExecutionFacade.execute(submissionId);

            verify(judgeExecutionPort, never()).submitBatch(any());
            verify(judgeExecutionPersistenceService, never())
                    .savePendingExecutions(any(), any(), any());
        }

        @Test
        @DisplayName("테스트케이스 파싱에 실패하면 예외를 전파하지 않고 INTERNAL_SYSTEM_ERROR로 FAILED 처리한다")
        void marksFailedWhenTestCaseParsingFails() {
            UUID submissionId = UUID.randomUUID();
            ProblemExecutionSpec spec = specWithTestCases("이건-JSON이-아님");
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            given(judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .willReturn(Optional.of(preparation));

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
            given(judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .willReturn(Optional.of(preparation));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            verify(judgeExecutionPort, never()).submitBatch(any());
            verify(judgeExecutionPersistenceService, never())
                    .savePendingExecutions(any(), any(), any());
        }

        @Test
        @DisplayName("Judge0 응답 개수가 요청 개수와 다르면 예외를 전파하지 않고 JUDGE0_RESPONSE_FAILURE로 FAILED 처리한다")
        void marksFailedWhenTokenCountMismatches() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new TestCaseItem(UUID.randomUUID(), true, "3 5", "8", 1),
                    new TestCaseItem(UUID.randomUUID(), false, "1 1", "2", 2)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            given(judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .willReturn(Optional.of(preparation));
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
            verify(judgeExecutionPersistenceService, never())
                    .savePendingExecutions(any(), any(), any());
        }

        @Test
        @DisplayName("Judge0 호출 자체가 실패하면 예외를 전파하지 않고 JUDGE0_RESPONSE_FAILURE로 FAILED 처리한다")
        void marksFailedWhenJudge0SubmitThrows() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new TestCaseItem(UUID.randomUUID(), true, "3 5", "8", 1)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            given(judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .willReturn(Optional.of(preparation));
            given(judgeExecutionPort.submitBatch(anyList()))
                    .willThrow(new RuntimeException("Judge0 연결 실패"));

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
        }

        @Test
        @DisplayName("토큰 저장이 실패하면 예외를 전파하지 않고 RESULT_SAVE_FAILURE로 FAILED 처리한다")
        void marksFailedWhenSavePendingExecutionsThrows() {
            UUID submissionId = UUID.randomUUID();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new TestCaseItem(UUID.randomUUID(), true, "3 5", "8", 1)
            ));
            ProblemExecutionSpec spec = specWithTestCases(testCasesJson);
            JudgeExecutionPreparation preparation =
                    new JudgeExecutionPreparation(submissionId, "public class Main {}", spec);
            given(judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .willReturn(Optional.of(preparation));
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1"));
            doThrow(new RuntimeException("DB 저장 실패"))
                    .when(judgeExecutionPersistenceService)
                    .savePendingExecutions(any(), any(), any());

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();

            verify(judgeExecutionPersistenceService).markFailed(submissionId, FailureCode.RESULT_SAVE_FAILURE);
        }

        @Test
        @DisplayName("FAILED 처리 자체가 실패해도 예외를 전파하지 않는다")
        void doesNotPropagateWhenMarkFailedItselfThrows() {
            UUID submissionId = UUID.randomUUID();
            given(judgeExecutionPersistenceService.prepareForExecution(submissionId))
                    .willReturn(Optional.of(new JudgeExecutionPreparation(
                            submissionId, "public class Main {}", specWithTestCases("이건-JSON이-아님"))));
            doThrow(new RuntimeException("FAILED 전이 자체도 실패"))
                    .when(judgeExecutionPersistenceService)
                    .markFailed(any(), any());

            assertThatCode(() -> judgeExecutionFacade.execute(submissionId)).doesNotThrowAnyException();
        }
    }
}