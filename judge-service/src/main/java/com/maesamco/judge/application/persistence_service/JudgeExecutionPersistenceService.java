package com.maesamco.judge.application.persistence_service;

import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent.TestCaseItem;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0Execution;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0ExecutionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JudgeExecutionFacade가 Judge0 HTTP 호출 앞뒤로 배치하는 짧은 DB 트랜잭션 조각
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JudgeExecutionPersistenceService {

    private final SubmissionRepository submissionRepository;
    private final ProblemExecutionSpecRepository problemExecutionSpecRepository;
    private final PendingJudge0ExecutionRepository pendingJudge0ExecutionRepository;

    // Judge0 호출 전 준비 단계 — Submission을 RUNNING으로 전이시키고, 채점에 필요한
    // 실행 명세를 같이 조회해서 Facade에 넘긴다. 이미 RUNNING이면(중복 이벤트) 빈 값을
    // 반환해서 Facade가 Judge0 호출 자체를 스킵하게 함.
    @Transactional
    public Optional<JudgeExecutionPreparation> prepareForExecution(UUID submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        if (submission.getStatus() == SubmissionStatus.RUNNING) {
            log.info("[Judge] 이미 RUNNING 상태인 중복 이벤트 — 재제출하지 않고 스킵. submissionId={}", submissionId);
            return Optional.empty();
        }
        submission.markRunning();

        ProblemExecutionSpec spec = problemExecutionSpecRepository
                .findByProblemIdAndProblemVersionId(submission.getProblemId(), submission.getProblemVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        return Optional.of(new JudgeExecutionPreparation(submission.getId(), submission.getCode(), spec));
    }

    // Judge0 배치 제출이 끝난 뒤 호출 — 토큰별로 PendingJudge0Execution을 저장.
    @Transactional
    public void savePendingExecutions(UUID submissionId, List<TestCaseItem> testCases, List<String> tokens) {
        List<PendingJudge0Execution> pendingExecutions = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            String token = tokens.get(i);
            if (token == null) {
                log.warn("[Judge] Judge0 토큰 누락 submissionId={}, testCaseId={}", submissionId, testCases.get(i).testCaseId());
                continue;
            }
            pendingExecutions.add(PendingJudge0Execution.create(submissionId, testCases.get(i).testCaseId(), token));
        }
        pendingJudge0ExecutionRepository.saveAll(pendingExecutions);
    }

    public record JudgeExecutionPreparation(UUID submissionId, String code, ProblemExecutionSpec spec) {
    }

    @Transactional
    public void markFailed(UUID submissionId, FailureCode failureCode) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));
        submission.markFailed(failureCode);
    }
}