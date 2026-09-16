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

import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    private static final int SAVE_RETRY_MAX_ATTEMPTS = 3;
    private static final long SAVE_RETRY_BACKOFF_MS = 200L;

    private final JudgeExecutionPersistenceService judgeExecutionPersistenceService;
    private final JudgeExecutionPort judgeExecutionPort;
    private final JsonMapper jsonMapper;

    public void execute(UUID submissionId) {
        Optional<JudgeExecutionPreparation> preparation;
        try {
            preparation = judgeExecutionPersistenceService.prepareForExecution(submissionId);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.info("[Judge] 다른 워커가 이미 처리 중 — 낙관적 락 충돌로 스킵. submissionId={}", submissionId);
                return;
        } catch (BusinessException e) {
            if(isNonRetryable(e.getErrorCode())) {
                log.error("[Judge] 실행 준비 단계에서 재시도 불가능한 오류 발생 - FAILED 처리, submissionId={}, errorCode={}",
                        submissionId, e.getErrorCode());
                markFailedSafely(submissionId,FailureCode.INTERNAL_SYSTEM_ERROR);
            } else {
                log.error("[Judge] 실행 준비 단계 실패 — 재시도 판단. submissionId={}, errorCode={}",
                        submissionId, e.getErrorCode(), e);
                handleRetryableFailureSafely(submissionId,FailureCode.INTERNAL_SYSTEM_ERROR);
            }
            return;
        } catch (Exception e) {
            log.error("[Judge] 실행 준비 단계에서 예상치 못한 오류 — 재시도 판단. submissionId={}", submissionId, e);
            handleRetryableFailureSafely(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            return;
        }

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
            log.error("[Judge] Judge0 제출 단계 실패 — 재시도 판단. submissionId={}", submissionId, e);
            handleRetryableFailureSafely(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
            return;
        }

        try {
            savePendingExecutionsWithRetry(submissionId, testCases, tokens);
        } catch (Exception e) {
            log.error("[Judge] 토큰 저장 단계에서 실패함 (재시도 {}회 소진) - Judge0 중복 제출 방지를 위해 재시도 스케쥴링 없이 FAILED 처리. "
                    + "submissionId={}, tokens={}", SAVE_RETRY_MAX_ATTEMPTS, submissionId, tokens, e);
            markFailedSafely(submissionId, FailureCode.RESULT_SAVE_FAILURE);
        }
    }

    private boolean isNonRetryable(ErrorCode errorCode) {
        return errorCode == ErrorCode.PROBLEM_NOT_FOUND || errorCode == ErrorCode.SUBMISSION_NOT_FOUND;
    }

    private void savePendingExecutionsWithRetry(UUID submissionId, List<ExecutionTestCase> testCases, List<String> tokens) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= SAVE_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                judgeExecutionPersistenceService.savePendingExecutions(submissionId, testCases, tokens);
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("[Judge] 토큰 저장 실패 — 재시도 {}/{}. submissionId={}",
                        attempt, SAVE_RETRY_MAX_ATTEMPTS, submissionId, e);
                if (attempt < SAVE_RETRY_MAX_ATTEMPTS) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        throw new IllegalStateException("토큰 저장 재시도 소진. submissionId=" + submissionId, lastException);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(SAVE_RETRY_BACKOFF_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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

    private void handleRetryableFailureSafely (UUID submissionId, FailureCode failureCode) {
        try {
            judgeExecutionPersistenceService.handleRetryableFailure(submissionId, failureCode);
        } catch (Exception e) {
            log.error("[Judge] 재시도/FAILED 처리 자체가 실패함 — 수동 확인 필요. submissionId={}, failureCode={}",
                    submissionId, failureCode, e);
        }
    }
}