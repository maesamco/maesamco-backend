package com.maesamco.judge.application.facade;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.persistence_service.JudgeResultPersistenceService;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0Execution;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0ExecutionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class JudgeResultPollingFacadeTest {

    @Mock
    private PendingJudge0ExecutionRepository pendingJudge0ExecutionRepository;
    @Mock
    private JudgeExecutionPort judgeExecutionPort;
    @Mock
    private JudgeResultPersistenceService judgeResultPersistenceService;

    @InjectMocks
    private JudgeResultPollingFacade judgeResultPollingFacade;

    @Nested
    @DisplayName("pollAndReflect")
    class PollAndReflect {

        @Test
        @DisplayName("한 건이 unique 제약 위반(중복 반영)으로 실패해도 나머지 건은 계속 반영한다")
        void continuesProcessingWhenOneItemFailsWithDuplicate() {
            PendingJudge0Execution pendingA =
                    PendingJudge0Execution.create(UUID.randomUUID(), UUID.randomUUID(), "token-a", true);
            PendingJudge0Execution pendingB =
                    PendingJudge0Execution.create(UUID.randomUUID(), UUID.randomUUID(), "token-b", true);
            given(pendingJudge0ExecutionRepository.findAllByOrderByCreatedAtAsc())
                    .willReturn(List.of(pendingA, pendingB));

            JudgeExecutionResult resultA = new JudgeExecutionResult(
                    "token-a", JudgeExecutionStatus.ACCEPTED, "3", null, null, 50L, 1024);
            JudgeExecutionResult resultB = new JudgeExecutionResult(
                    "token-b", JudgeExecutionStatus.ACCEPTED, "3", null, null, 50L, 1024);
            given(judgeExecutionPort.fetchResults(List.of("token-a", "token-b")))
                    .willReturn(List.of(resultA, resultB));

            // 다른 인스턴스가 먼저 처리해서 A는 unique 제약 위반으로 실패한다고 가정
            willThrow(new DataIntegrityViolationException("duplicate key"))
                    .given(judgeResultPersistenceService).reflectResult(pendingA, resultA);
            willDoNothing().given(judgeResultPersistenceService).reflectResult(pendingB, resultB);

            assertThatCode(() -> judgeResultPollingFacade.pollAndReflect()).doesNotThrowAnyException();

            verify(judgeResultPersistenceService).reflectResult(pendingA, resultA);
            verify(judgeResultPersistenceService).reflectResult(pendingB, resultB);
        }

        @Test
        @DisplayName("아직 채점 중(IN_QUEUE/PROCESSING)인 결과는 반영을 스킵한다")
        void skipsStillProcessingResults() {
            PendingJudge0Execution pending =
                    PendingJudge0Execution.create(UUID.randomUUID(), UUID.randomUUID(), "token-a", true);
            given(pendingJudge0ExecutionRepository.findAllByOrderByCreatedAtAsc())
                    .willReturn(List.of(pending));

            JudgeExecutionResult stillProcessing = new JudgeExecutionResult(
                    "token-a", JudgeExecutionStatus.PROCESSING, null, null, null, null, null);
            given(judgeExecutionPort.fetchResults(List.of("token-a")))
                    .willReturn(List.of(stillProcessing));

            judgeResultPollingFacade.pollAndReflect();

            verify(judgeResultPersistenceService, org.mockito.Mockito.never()).reflectResult(any(), any());
        }
    }
}