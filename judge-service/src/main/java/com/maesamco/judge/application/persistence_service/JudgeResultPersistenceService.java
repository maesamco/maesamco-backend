package com.maesamco.judge.application.persistence_service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
        if (result.status() == JudgeExecutionStatus.COMPILE_ERROR) {
            // 컴파일 에러는 제출 전체 단위 결과라 테스트케이스별 SubmissionTestResult를 만들지 않음.
            // 남은 pending 건 전부 정리하고 즉시 COMPILE_ERROR로 종료.
            pendingJudge0ExecutionRepository.deleteAll(
                    pendingJudge0ExecutionRepository.findAllBySubmissionId(pending.getSubmissionId()));
            completeSubmissionWithResult(pending.getSubmissionId(), SubmissionResult.COMPILE_ERROR, result);
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
        SubmissionTestErrorType errorType = passed ? null : resolveErrorType(pending, result);

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
            completeSubmissionWithResult(pending.getSubmissionId(), overallResult, result);
        }
    }

    private SubmissionTestErrorType resolveErrorType(PendingJudge0Execution pending, JudgeExecutionResult result) {
        if (isMemoryLimitExceeded(pending, result)) {
            return SubmissionTestErrorType.MEMORY_LIMIT_EXCEEDED;
        }
        return toErrorType(result.status());
    }

    private boolean isMemoryLimitExceeded(PendingJudge0Execution pending,JudgeExecutionResult result) {
        if (result.memoryUsedKb() == null) {
            return false;
        }
        Submission submission = submissionRepository.findById(pending.getSubmissionId())
                .orElseThrow(()-> new IllegalStateException("제출을 찾을 수 없습니다.:" + pending.getSubmissionId()));
        ProblemExecutionSpec spec = problemExecutionSpecRepository
                .findByProblemIdAndProblemVersionId(submission.getProblemId(), submission.getProblemVersionId())
                .orElseThrow(()-> new IllegalStateException(
                        "채점 기준을 찾을 수 없습니다. submissionId=" + pending.getSubmissionId()));
        return result.memoryUsedKb() > spec.getMemoryLimitMb() * KB_PER_MB;
    }

    private void completeSubmissionWithResult(UUID submissionId, SubmissionResult overallResult, JudgeExecutionResult lastResult) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));

        if (submission.getStatus() == SubmissionStatus.COMPLETED) {
            log.warn("[Judge] 이미 COMPLETED 처리된 제출에 완료 처리가 중복 호출됨 — SubmissionJudged 재발행 스킵. submissionId={}",
                    submissionId);
            return;
        }

        int executionTimeMs = lastResult.executionTimeMs() != null ? lastResult.executionTimeMs().intValue() : 0;
        int memoryUsedKb = lastResult.memoryUsedKb() != null ? lastResult.memoryUsedKb() : 0;

        submission.markCompleted(overallResult, executionTimeMs, memoryUsedKb);
        submissionRepository.save(submission);

        String payload = writeSubmissionJudgedPayload(submission, overallResult);

        submissionEventOutboxRepository.save(
                SubmissionEventOutbox.create(submissionId, SUBMISSION_JUDGED_EVENT_TYPE, payload));
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
