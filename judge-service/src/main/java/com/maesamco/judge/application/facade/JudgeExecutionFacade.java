package com.maesamco.judge.application.facade;

import com.maesamco.judge.application.command.ExecutionTestCase;
import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService;
import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService.JudgeExecutionPreparation;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionRequest;
import com.maesamco.judge.domain.entity.FailureCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Judge0 채점 실행 — JudgeRequestedConsumer가 호출.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JudgeExecutionFacade {

    private final JudgeExecutionPersistenceService judgeExecutionPersistenceService;
    private final JudgeExecutionPort judgeExecutionPort;
    private final JsonMapper jsonMapper;

    public void execute(UUID submissionId) {
        Optional<JudgeExecutionPreparation> preparation =
                judgeExecutionPersistenceService.prepareForExecution(submissionId);
        if (preparation.isEmpty()) {
            return;
        }

        JudgeExecutionPreparation prep = preparation.get();

        List<ExecutionTestCase> testCases;
        try {
            testCases = parseTestCases(prep.spec().getTestCases());
        } catch (Exception e) {
            log.error("[Judge] 테스트케이스 파싱 실패 — FAILED 처리. submissionId={}", submissionId, e);
            markFailedSafely(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            return;
        }

        // Judge0에 빈 batch 보내고 나서 사후처리하지 않고, 애초에 무의미한 외부 호출 자체를 안하도록 검증.
        if (testCases.isEmpty()) {
            log.error("[Judge] 실행 명세에 테스트케이스가 없음 — Judge0 호출 없이 FAILED 처리. submissionId={}", submissionId);
            markFailedSafely(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            return;
        }

        List<String> tokens;
        try {
            List<JudgeExecutionRequest> requests = testCases.stream()
                    .map(tc -> new JudgeExecutionRequest(
                            prep.code(), tc.input(), tc.expectedOutput(),
                            prep.spec().getTimeLimitMs() / 1000.0, prep.spec().getMemoryLimitMb() * 1024))
                    .toList();
            tokens = judgeExecutionPort.submitBatch(requests);
            if (tokens.size() != testCases.size()) {
                log.error("[Judge] Judge0 응답 개수 불일치. submissionId={}, 요청={}, 응답={}",
                        submissionId, testCases.size(), tokens.size());
                throw new IllegalStateException(
                        "Judge0 batch 응답 개수 불일치. submissionId=" + submissionId
                                + ", 요청=" + testCases.size() + ", 응답=" + tokens.size());
            }
        } catch (Exception e) {
            log.error("[Judge] Judge0 제출 단계 실패 — FAILED 처리. submissionId={}", submissionId, e);
            markFailedSafely(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
            return;
        }

        try {
            judgeExecutionPersistenceService.savePendingExecutions(submissionId, testCases, tokens);
        } catch (Exception e) {
            log.error("[Judge] 토큰 저장 단계 실패 — FAILED 처리. submissionId={}", submissionId, e);
            markFailedSafely(submissionId, FailureCode.RESULT_SAVE_FAILURE);
        }
    }


    private void markFailedSafely(UUID submissionId, FailureCode failureCode) {
        try {
            judgeExecutionPersistenceService.markFailed(submissionId, failureCode);
        } catch (Exception e) {
            log.error("[Judge] FAILED 처리 자체가 실패함 — 수동 확인 필요. submissionId={}, failureCode={}",
                    submissionId, failureCode, e);
        }
    }

    private List<ExecutionTestCase> parseTestCases(String testCasesJson) {
        try {
            return jsonMapper.readValue(testCasesJson, new TypeReference<List<ExecutionTestCase>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("testCases JSON 파싱 실패", e);
        }
    }
}