package com.maesamco.judge.application.facade;

import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService;
import com.maesamco.judge.application.persistence_service.JudgeExecutionPersistenceService.JudgeExecutionPreparation;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionRequest;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent.TestCaseItem;
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
        List<TestCaseItem> testCases = parseTestCases(prep.spec().getTestCases());

        List<JudgeExecutionRequest> requests = testCases.stream()
                .map(tc -> new JudgeExecutionRequest(
                        prep.code(),
                        tc.input(),
                        tc.expectedOutput(),
                        prep.spec().getTimeLimitMs() / 1000.0,
                        prep.spec().getMemoryLimitMb() * 1024
                ))
                .toList();

        List<String> tokens = judgeExecutionPort.submitBatch(requests);

        if (tokens.size() != testCases.size()) {
            log.error("[Judge] Judge0 응답 개수 불일치. submissionId={}, 요청={}, 응답={}",
                    submissionId, testCases.size(), tokens.size());
            throw new IllegalStateException(
                    "Judge0 batch 응답 개수 불일치. submissionId=" + submissionId
                            + ", 요청=" + testCases.size() + ", 응답=" + tokens.size());
        }

        judgeExecutionPersistenceService.savePendingExecutions(submissionId, testCases, tokens);
    }

    private List<TestCaseItem> parseTestCases(String testCasesJson) {
        try {
            return jsonMapper.readValue(testCasesJson, new TypeReference<List<TestCaseItem>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("testCases JSON 파싱 실패", e);
        }
    }
}