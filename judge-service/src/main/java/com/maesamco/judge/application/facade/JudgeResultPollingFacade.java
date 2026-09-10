package com.maesamco.judge.application.facade;

import com.maesamco.judge.application.persistence_service.JudgeResultPersistenceService;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0Execution;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0ExecutionRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeResultPollingFacade {

    private final PendingJudge0ExecutionRepository pendingJudge0ExecutionRepository;
    private final JudgeExecutionPort judgeExecutionPort;
    private final JudgeResultPersistenceService judgeResultPersistenceService;

    public void pollAndReflect() {
        List<PendingJudge0Execution> pendingList = pendingJudge0ExecutionRepository.findAllByOrderByCreatedAtAsc();
        if (pendingList.isEmpty()) {
            return;
        }

        List<String> tokens = pendingList.stream().map(PendingJudge0Execution::getJudge0Token).toList();
        List<JudgeExecutionResult> results = judgeExecutionPort.fetchResults(tokens);

        Map<String, PendingJudge0Execution> pendingByToken = pendingList.stream()
                .collect(java.util.stream.Collectors.toMap(PendingJudge0Execution::getJudge0Token, p -> p));

        for (JudgeExecutionResult result : results) {
            if (isStillProcessing(result.status())) {
                continue; // 아직 Judge0에서 채점 진행 중 — 다음 폴링 때 다시 확인
            }

            PendingJudge0Execution pending = pendingByToken.get(result.token());
            if (pending == null) {
                log.warn("[Judge] 결과는 왔는데 매칭되는 pending 건이 없음 token={}", result.token());
                continue;
            }
            try {
                judgeResultPersistenceService.reflectResult(pending, result);
            } catch (Exception e) {
                log.error("[Judge] 결과 반영 실패 submissionId={}, testCaseId={}",
                        pending.getSubmissionId(), pending.getTestCaseId(), e);
            }
        }
    }
    private boolean isStillProcessing(JudgeExecutionStatus status) {
        return status == JudgeExecutionStatus.IN_QUEUE || status == JudgeExecutionStatus.PROCESSING;
    }
}
