package com.maesamco.judge.application.persistence_service;

import com.maesamco.judge.application.exception.ProblemExecutionSpecNotFoundException;
import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import com.maesamco.judge.domain.entity.*;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.domain.repository.SubmissionTestResultRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0Execution;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0ExecutionRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static com.maesamco.judge.domain.entity.SubmissionResult.MEMORY_LIMIT_EXCEEDED;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JudgeResultPersistenceService {

    private static final String SUBMISSION_JUDGED_EVENT_TYPE = "SubmissionJudged";
    private static final int KB_PER_MB = 1024;
    private static final int MAX_MEMORY_CHECK_RETRY = 3;

    private final PendingJudge0ExecutionRepository pendingJudge0ExecutionRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionEventOutboxRepository submissionEventOutboxRepository;
    private final ProblemExecutionSpecRepository problemExecutionSpecRepository;
    private final JudgeExecutionPersistenceService judgeExecutionPersistenceService;
    private final JsonMapper jsonMapper;

    /**
     * 토큰 하나(=테스트케이스 하나)의 Judge0 결과를 반영.
     * 이 제출의 모든 테스트케이스가 다 반영되면 Submission을 COMPLETED로 전이시키고
     * SubmissionJudged 발행용 Outbox 행을 남김.
     */
    public void reflectResult(PendingJudge0Execution pending, JudgeExecutionResult result) {
        Submission submission = submissionRepository.findById(pending.getSubmissionId())
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + pending.getSubmissionId()));

        if (isTerminal(submission.getStatus())) {
            // 같은 폴링 배치 안에서 COMPILE_ERROR(또는 시스템 실패)가 먼저 이 제출을 종료시킨 경우 —
            // 나머지 결과가 이어서 반영되며 이미 terminal인 제출에 결과가 덧붙는 것을 방지.
            log.warn("[Judge] 이미 종료 상태({})인 제출에 결과 반영이 호출됨 — 결과는 버리고 pending만 정리. "
                            + "submissionId={}, testCaseId={}",
                    submission.getStatus(), pending.getSubmissionId(), pending.getTestCaseId());
            pendingJudge0ExecutionRepository.delete(pending);
            return;
        }

        if (result.status() == JudgeExecutionStatus.COMPILE_ERROR) {
            // 컴파일 에러는 제출 전체 단위 결과라 테스트케이스별 SubmissionTestResult를 만들지 않음.
            // 남은 pending 건 전부 정리하고 즉시 COMPILE_ERROR로 종료.
            pendingJudge0ExecutionRepository.deleteAll(
                    pendingJudge0ExecutionRepository.findAllBySubmissionId(pending.getSubmissionId()));
            completeSubmissionWithResult(submission, SubmissionResult.COMPILE_ERROR, result);
            return;
        }

        if (isSystemFailure(result.status())) {
            // Judge0 자체 실패(INTERNAL_ERROR)나 매핑 불가능한 상태(UNKNOWN)는 학생 오답이 아니라
            // 채점 시스템 실패 — WRONG_ANSWER로 흘려보내지 않고 FAILED로 종료한다.
            log.error("[Judge] Judge0 실행 결과가 시스템 실패 상태({})로 반환됨 — FAILED 처리. "
                            + "submissionId={}, testCaseId={}, token={}",
                    result.status(), pending.getSubmissionId(), pending.getTestCaseId(), pending.getJudge0Token());
            pendingJudge0ExecutionRepository.deleteAll(
                    pendingJudge0ExecutionRepository.findAllBySubmissionId(pending.getSubmissionId()));
            try {
                judgeExecutionPersistenceService.markFailed(pending.getSubmissionId(), FailureCode.JUDGE0_RESPONSE_FAILURE);
            } catch (BusinessException e) {
                log.warn("[Judge] 이미 종료 상태로 전이돼 있어 markFailed를 건너뜀. submissionId={}",
                        pending.getSubmissionId(), e);
            }
            return;
        }

        boolean passed = result.status() == JudgeExecutionStatus.ACCEPTED;
        SubmissionTestErrorType errorType;
        if (passed) {
            errorType = null;
        } else {
            try {
                errorType = resolveErrorType(submission, pending, result);
            } catch (ProblemExecutionSpecNotFoundException e) {
                handleUnresolvableReference(pending, e);
                return;
            }
        }

        SubmissionTestResult testResult = SubmissionTestResult.create(
                pending.getSubmissionId(), pending.getTestCaseId(), pending.isPublic(), passed, result.stdout(), errorType);
        submissionTestResultRepository.save(testResult);
        pendingJudge0ExecutionRepository.delete(pending);

        boolean allDone = pendingJudge0ExecutionRepository
                .findAllBySubmissionId(pending.getSubmissionId())
                .isEmpty();

        if (allDone) {
            List<SubmissionTestResult> failed =
                    submissionTestResultRepository.findBySubmissionIdAndPassedFalse(pending.getSubmissionId());
            SubmissionResult overallResult = failed.isEmpty()
                    ? SubmissionResult.CORRECT
                    : toSubmissionResult(pickMostSevere(failed));
            completeSubmissionWithResult(submission, overallResult, result);
        }
    }

    private SubmissionTestErrorType resolveErrorType(Submission submission, PendingJudge0Execution pending, JudgeExecutionResult result) {
        if (isMemoryLimitExceeded(submission, pending, result)) {
            return SubmissionTestErrorType.MEMORY_LIMIT_EXCEEDED;
        }
        return toErrorType(result.status());
    }

    private boolean isMemoryLimitExceeded(Submission submission, PendingJudge0Execution pending, JudgeExecutionResult result) {
        if (result.memoryUsedKb() == null) {
            return false;
        }
        ProblemExecutionSpec spec = problemExecutionSpecRepository
                .findByProblemIdAndProblemVersionId(submission.getProblemId(), submission.getProblemVersionId())
                .orElseThrow(() -> new ProblemExecutionSpecNotFoundException(
                        "채점 기준을 찾을 수 없습니다. submissionId=" + pending.getSubmissionId()));
        return result.memoryUsedKb() > spec.getMemoryLimitMb() * KB_PER_MB;
    }

    private void handleUnresolvableReference(PendingJudge0Execution pending, ProblemExecutionSpecNotFoundException e) {
        pending.increaseRetryCount();

        if (pending.getRetryCount() >= MAX_MEMORY_CHECK_RETRY) {
            log.error("[Judge] 참조 데이터 누락으로 {}회 재시도 후 포기 — FAILED 처리. submissionId={}, testCaseId={}",
                    pending.getRetryCount(), pending.getSubmissionId(), pending.getTestCaseId(), e);
            pendingJudge0ExecutionRepository.deleteAll(
                    pendingJudge0ExecutionRepository.findAllBySubmissionId(pending.getSubmissionId()));
            try {
                judgeExecutionPersistenceService.markFailed(pending.getSubmissionId(), FailureCode.INTERNAL_SYSTEM_ERROR);
            } catch (BusinessException be) {
                log.warn("[Judge] 이미 종료 상태로 전이돼 있어 markFailed를 건너뜀. submissionId={}",
                        pending.getSubmissionId(), be);
            }
            return;
        }

        log.warn("[Judge] 참조 데이터 누락 — {}회째 재시도 예정. submissionId={}, testCaseId={}",
                pending.getRetryCount(), pending.getSubmissionId(), pending.getTestCaseId(), e);
        pendingJudge0ExecutionRepository.save(pending);
    }

    private void completeSubmissionWithResult(Submission submission, SubmissionResult overallResult, JudgeExecutionResult lastResult) {
        // reflectResult()가 진입 시점에 이미 terminal 여부를 확인했으므로 여기서는 별도 가드 없이 진행.
        // (멀티 인스턴스 동시 완료는 Submission.version 기반 낙관적 락 + 상위 호출부(JudgeResultPollingFacade)의
        // try/catch가 방어
        int executionTimeMs = lastResult.executionTimeMs() != null ? lastResult.executionTimeMs().intValue() : 0;
        int memoryUsedKb = lastResult.memoryUsedKb() != null ? lastResult.memoryUsedKb() : 0;

        submission.markCompleted(overallResult, executionTimeMs, memoryUsedKb);
        submissionRepository.save(submission);

        String payload = writeSubmissionJudgedPayload(submission, overallResult);

        submissionEventOutboxRepository.save(
                SubmissionEventOutbox.create(submission.getId(), SUBMISSION_JUDGED_EVENT_TYPE, payload));
    }

    private SubmissionTestErrorType toErrorType(JudgeExecutionStatus status) {
        return switch (status) {
            case TIME_LIMIT_EXCEEDED -> SubmissionTestErrorType.TIME_LIMIT_EXCEEDED;
            case RUNTIME_ERROR -> SubmissionTestErrorType.RUNTIME_ERROR;
            default -> SubmissionTestErrorType.WRONG_ANSWER;
        };
    }

    private SubmissionResult toSubmissionResult(SubmissionTestErrorType errorType) {
        return switch (errorType) {
            case TIME_LIMIT_EXCEEDED -> SubmissionResult.TIME_LIMIT_EXCEEDED;
            case RUNTIME_ERROR -> SubmissionResult.RUNTIME_ERROR;
            case MEMORY_LIMIT_EXCEEDED -> MEMORY_LIMIT_EXCEEDED;
            default -> SubmissionResult.WRONG;
        };
    }

    private SubmissionTestErrorType pickMostSevere(List<SubmissionTestResult> failed) {
        return failed.stream()
                .map(SubmissionTestResult::getErrorType)
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(SEVERITY_ORDER::indexOf))
                .orElse(SubmissionTestErrorType.WRONG_ANSWER);
    }

    private String writeSubmissionJudgedPayload(Submission submission, SubmissionResult overallResult) {
        try {
            return jsonMapper.writeValueAsString(SubmissionJudgedPayload.of(submission, overallResult));
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "SubmissionJudged payload 직렬화 실패. submissionId=" + submission.getId(), e);
        }
    }

    private boolean isSystemFailure(JudgeExecutionStatus status) {
        return status == JudgeExecutionStatus.INTERNAL_ERROR || status == JudgeExecutionStatus.UNKNOWN;
    }

    private boolean isTerminal(SubmissionStatus status) {
        return status == SubmissionStatus.COMPLETED || status == SubmissionStatus.FAILED;
    }

    private static final List<SubmissionTestErrorType> SEVERITY_ORDER = List.of(
            SubmissionTestErrorType.RUNTIME_ERROR,
            SubmissionTestErrorType.MEMORY_LIMIT_EXCEEDED,
            SubmissionTestErrorType.TIME_LIMIT_EXCEEDED,
            SubmissionTestErrorType.WRONG_ANSWER
    );

    private record SubmissionJudgedPayload(
            UUID submissionId,
            UUID userId,
            UUID problemId,
            String status,
            String result
    ) {
        static SubmissionJudgedPayload of(Submission submission, SubmissionResult overallResult) {
            return new SubmissionJudgedPayload(
                    submission.getId(),
                    submission.getUserId(),
                    submission.getProblemId(),
                    SubmissionStatus.COMPLETED.name(),
                    overallResult.name());
        }
    }
}
