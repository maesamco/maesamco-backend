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

    // 이슈 #350 — Outbox 릴레이는 Kafka 발행 직후에 PENDING → QUEUED 전이를 커밋한다. 그 몇 ms 사이에
    // 소비자가 같은 Submission을 읽으면 낙관적 락 충돌이 나는데, 충돌 상대는 다른 채점 워커가 아니라
    // 릴레이의 상태 전이라서 잠깐 뒤 다시 읽으면(QUEUED) 정상적으로 RUNNING 전이가 된다.
    private static final int MARK_RUNNING_MAX_ATTEMPTS = 3;
    private static final long MARK_RUNNING_BACKOFF_MS = 100L;

    private final JudgeExecutionPersistenceService judgeExecutionPersistenceService;
    private final JudgeExecutionPort judgeExecutionPort;
    private final JsonMapper jsonMapper;

    public void execute(UUID submissionId) {
        Optional<UUID> markedRunning;
        try {
            markedRunning = markRunningIfNeededWithRetry(submissionId);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 재시도해도 계속 충돌하면 여기서 정상 종료하지 않는다 — 정상 종료하면 Kafka 메시지가 소비 완료로
            // 처리돼 재전달되지 않고 제출이 QUEUED에 영구히 남는다(이슈 #350). 예외를 컨테이너의 에러
            // 핸들러(재시도 후 DLT)로 넘긴다.
            log.warn("[Judge] RUNNING 전이가 낙관적 락 충돌로 재시도를 마쳤지만 실패 — 재전달을 위해 예외를 전파. submissionId={}",
                    submissionId);
            throw e;
        } catch (BusinessException e) {
            // SUBMISSION_NOT_FOUND, 이미 종료 상태로 전이돼 RUNNING이 될 수 없는 경우처럼 다시 받아도 결과가
            // 달라지지 않는 도메인 오류만 정상 종료한다. RUNNING 전이가 커밋된 적이 없으므로 상태는 건드리지 않는다.
            log.error("[Judge] RUNNING 전이 불가(재시도해도 결과가 같은 도메인 오류) — 상태 변경 없이 종료. submissionId={}, errorCode={}",
                    submissionId, e.getErrorCode(), e);
            return;
        }
        // 그 밖의 예외(일시적 DB 오류, 커넥션 풀 고갈, 트랜잭션 예외 등)는 잡지 않고 그대로 전파한다.
        // 여기서 정상 종료하면 Kafka 메시지가 소비 완료로 처리돼 재전달되지 않고, 제출이 QUEUED(또는
        // 릴레이 후처리까지 실패했다면 PENDING)에 남는다 — 복구 스케줄러는 QUEUED만 집으므로 PENDING은 복구되지도 않는다.
        // 예외를 컨테이너의 에러 핸들러(재시도 후 DLT)로 넘겨 재전달되게 한다.

        if (markedRunning.isEmpty()) {
            return;
        }

        JudgeExecutionPreparation preparation;
        try {
            preparation = judgeExecutionPersistenceService.loadExecutionPreparation(submissionId);
        } catch (BusinessException e) {
            if (isNonRetryable(e.getErrorCode())) {
                log.error("[Judge] 실행 준비 단계에서 재시도 불가능한 오류 발생 - FAILED 처리, submissionId={}, errorCode={}",
                        submissionId, e.getErrorCode());
                markFailedSafely(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            } else {
                log.error("[Judge] 실행 준비 단계 실패 — 재시도 판단. submissionId={}, errorCode={}",
                        submissionId, e.getErrorCode(), e);
                handleRetryableFailureSafely(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            }
            return;
        } catch (Exception e) {
            log.error("[Judge] 실행 준비 단계에서 예상치 못한 오류 — 재시도 판단. submissionId={}", submissionId, e);
            handleRetryableFailureSafely(submissionId, FailureCode.INTERNAL_SYSTEM_ERROR);
            return;
        }

        List<ExecutionTestCase> testCases;
        try {
            testCases = parseTestCases(preparation.spec().getTestCases());
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
                            preparation.code(), tc.input(), tc.expectedOutput(),
                            preparation.spec().getTimeLimitMs() / 1000.0, preparation.spec().getMemoryLimitMb() * 1024))
                    .toList();
            tokens = judgeExecutionPort.submitBatch(requests);
            if (tokens.size() != testCases.size()) {
                log.error("[Judge] Judge0 응답 개수 불일치. submissionId={}, 요청={}, 응답={}",
                        submissionId, testCases.size(), tokens.size());
                throw new IllegalStateException(
                        "Judge0 batch 응답 개수 불일치. submissionId=" + submissionId
                                + ", 요청=" + testCases.size() + ", 응답=" + tokens.size());
            }
            // ⚠️ 리뷰 반영(#293 P1) — submitBatch()는 이제 청크가 부분 실패해도 예외를
            // 던지지 않고 실패한 항목만 token=null로 채워 반환한다(청크 중 일부만 실패한
            // 경우, 이미 성공한 청크까지 재시도로 중복 제출되는 걸 막기 위함). 다만
            // "전부"(모든 청크) 실패한 경우엔 성공한 게 하나도 없으니 중복 위험 없이
            // 안전하게 전체 재시도를 태워야 한다 — 예전(예외를 던지던 방식)과 동일한
            // 동작을 이 경우에 한해 복원한다.
            if (tokens.stream().allMatch(java.util.Objects::isNull)) {
                log.error("[Judge] Judge0 제출 전체 실패(모든 청크 실패) — 재시도 판단. submissionId={}", submissionId);
                handleRetryableFailureSafely(submissionId, FailureCode.JUDGE0_RESPONSE_FAILURE);
                return;
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

    /**
     * RUNNING 전이를 시도하고, 낙관적 락 충돌이면 잠깐 대기한 뒤 최신 상태로 다시 시도한다.
     *
     * <p>다시 시도하면 두 경우가 모두 올바르게 수렴한다 — 릴레이의 QUEUED 전이가 끝났으면 정상 전이되고,
     * 다른 워커가 실제로 RUNNING으로 바꿨으면 markRunningIfNeeded가 빈 값을 돌려줘 스킵된다.</p>
     */
    private Optional<UUID> markRunningIfNeededWithRetry(UUID submissionId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return judgeExecutionPersistenceService.markRunningIfNeeded(submissionId);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= MARK_RUNNING_MAX_ATTEMPTS) {
                    throw e;
                }
                log.info("[Judge] RUNNING 전이가 낙관적 락 충돌 — 최신 상태로 재시도. attempt={}/{}, submissionId={}",
                        attempt, MARK_RUNNING_MAX_ATTEMPTS, submissionId);
                if (!sleep(MARK_RUNNING_BACKOFF_MS * attempt)) {
                    // 종료/취소 신호(interrupt)를 받았다 — 더 재시도하지 않고 충돌 예외를 전파해 메시지가
                    // 재전달되게 한다(다음 인스턴스가 이어받는다).
                    log.warn("[Judge] RUNNING 전이 재시도 대기 중 인터럽트 — 재시도를 중단하고 예외를 전파. submissionId={}",
                            submissionId);
                    throw e;
                }
            }
        }
    }

    /** @return 끝까지 대기했으면 true, 인터럽트로 중단됐으면 false(인터럽트 플래그는 복구해 둔다) */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
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