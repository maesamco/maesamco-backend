package com.maesamco.judge.application.command_service;

import com.maesamco.judge.domain.entity.SubmissionStatus;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionRequest;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent.TestCaseItem;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0Execution;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0ExecutionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeExecutionCommandService {

    private final SubmissionRepository submissionRepository;
    private final ProblemExecutionSpecRepository problemExecutionSpecRepository;
    private final JudgeExecutionPort judgeExecutionPort;
    private final PendingJudge0ExecutionRepository pendingJudge0ExecutionRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public void execute(UUID submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        if (submission.getStatus() == SubmissionStatus.RUNNING) {
            log.info("[Judge] 이미 RUNNING 상태인 중복 이벤트 — 재제출하지 않고 스킵. submissionId={}", submissionId);
            return;
        }
        submission.markRunning();

        ProblemExecutionSpec spec = problemExecutionSpecRepository
                .findByProblemIdAndProblemVersionId(submission.getProblemId(), submission.getProblemVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        List<TestCaseItem> testCases = parseTestCases(spec.getTestCases());

        List<JudgeExecutionRequest> requests = testCases.stream()
                .map(tc -> new JudgeExecutionRequest(
                        submission.getCode(),
                        tc.input(),
                        tc.expectedOutput(),
                        spec.getTimeLimitMs() / 1000.0,
                        spec.getMemoryLimitMb() * 1024
                ))
                .toList();

        List<String> tokens = judgeExecutionPort.submitBatch(requests);

        savePendingExecutions(submission.getId(), testCases, tokens);
    }

    private void savePendingExecutions(UUID submissionId, List<TestCaseItem> testCases, List<String> tokens) {
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

    private List<TestCaseItem> parseTestCases(String testCasesJson) {
        try {
            return jsonMapper.readValue(testCasesJson, new TypeReference<List<TestCaseItem>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("testCases JSON 파싱 실패", e);
        }
    }
}