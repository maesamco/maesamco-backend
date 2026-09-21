package com.maesamco.judge.application.facade;

import com.maesamco.judge.application.command.ExecutionValidationTestCase;
import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionRequest;
import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import com.maesamco.judge.application.result.ExecutionValidationResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionValidationFacade {

    @Value("${judge0.validation.max-poll-attempts:10}")
    private int maxPollAttempts;

    @Value("${judge0.validation.poll-interval-ms:500}")
    private long pollIntervalMs;

    @Value("${judge0.validation.max-consecutive-fetch-failures:3}")
    private int maxConsecutiveFetchFailures;

    private final JudgeExecutionPort judgeExecutionPort;

    public List<ExecutionValidationResult> validate(String code, List<ExecutionValidationTestCase> testCases) {

        //testCase가 빈 리스트로 들어오는 경우 가드
        if (testCases.isEmpty()) {
            return List.of();
        }

        List<JudgeExecutionRequest> requests = testCases.stream()
                .map(tc -> new JudgeExecutionRequest(
                        code, tc.input(), tc.expectedOutput(),
                        tc.cpuTimeLimitSeconds(), tc.memoryLimitKb()))
                .toList();

        List<String> tokens = judgeExecutionPort.submitBatch(requests);
        if (tokens.size() != testCases.size()) {
            log.error("[Judge] 검증용 실행 batch 제출 응답 개수 불일치. 요청={}, 응답={}",
                    testCases.size(), tokens.size());
            throw new IllegalStateException("Judge0 batch 제출 응답 개수가 요청과 다릅니다.");
        }

        List<String> submittedTokens = tokens.stream().filter(Objects::nonNull).toList();
        Map<String, JudgeExecutionResult> resultsByToken =
                submittedTokens.isEmpty() ? Map.of() : pollUntilDone(submittedTokens);


        return IntStream.range(0, tokens.size())
                .mapToObj(i -> toValidationResult(i, tokens.get(i), resultsByToken))
                .toList();
    }

    private ExecutionValidationResult toValidationResult(int index, String token, Map<String, JudgeExecutionResult> resultsByToken) {
        if (token == null) {
            // 제출 자체가 실패한 케이스 — 결과를 알 수 없으니 timedOut으로 표시
            return new ExecutionValidationResult(index, false, true, false, null, null, null);
        }
        JudgeExecutionResult result = resultsByToken.get(token);
        if (result == null) {
            // 폴링을 다 돌았는데도 이 토큰의 응답이 끝내 안 온 케이스 - 결과를 알 수 없으니 이것도 timeOut으로 표시
            return new ExecutionValidationResult(index, false, true, false, null, null, null);
        }
        if (isPending(result.status())) {
            return new ExecutionValidationResult(index, false, true, false, result.stdout(), result.stderr(), result.compileOutput());
        }
        if (isSystemFailure(result.status())) {
            // Judge0 자체 실패나 매핑 불가능한 상태 — 코드가 틀린 게 아니라 채점 시스템이 판정을 못한 것
            return new ExecutionValidationResult(index, false, false, true, result.stdout(), result.stderr(), result.compileOutput());
        }
        boolean passed = result.status() == JudgeExecutionStatus.ACCEPTED;
        return new ExecutionValidationResult(index, passed, false, false, result.stdout(), result.stderr(), result.compileOutput());
    }

    private Map<String, JudgeExecutionResult> pollUntilDone(List<String> tokens) {
        Map<String, JudgeExecutionResult> latest = new HashMap<>();
        int attempt = 0;
        int consecutiveFailures = 0;
        boolean firstCall = true;

        while (attempt < maxPollAttempts && hasPending(tokens, latest)) {
            if (!firstCall) {
                sleep(pollIntervalMs);
            }
            firstCall = false;
            try {
                latest.putAll(fetchAsMap(tokens));
                consecutiveFailures = 0;
                attempt++;
            } catch (Exception e) {
                consecutiveFailures++;
                log.warn("[Judge] 검증용 실행 결과 조회 실패({}/{}회 연속) — 재시도. 토큰 개수={}",
                        consecutiveFailures, maxConsecutiveFetchFailures, tokens.size(), e);
                if (consecutiveFailures >= maxConsecutiveFetchFailures) {
                    throw new IllegalStateException(
                            "Judge0 결과 조회가 %d회 연속 실패함".formatted(consecutiveFailures), e);
                }
            }
        }
        if (hasPending(tokens, latest)) {
            log.warn("[Judge] 검증용 실행이 polling 예산(최대 {}회, 정상 응답 기준 약 {}ms 소요 추정) 내에 끝나지 않음 — "
                            + "fetchResults() 자체의 응답 지연이나 조회 실패 재시도로 실제 소요시간은 더 길 수 있음. 토큰 개수={}",
                    maxPollAttempts, (maxPollAttempts - 1) * pollIntervalMs, tokens.size());
        }
        return latest;
    }

    private Map<String, JudgeExecutionResult> fetchAsMap(List<String> tokens) {
        return judgeExecutionPort.fetchResults(tokens).stream()
                .collect(Collectors.toMap(JudgeExecutionResult::token, r -> r));
    }

    /**
     * 요청한 토큰 중 하나라도 (a) 아직 진행 중이거나 (b) 이번 응답에 아예 없으면 "아직 안 끝남"으로 본다.
     * (b)를 pending으로 취급하도록 함. Judge0가 빈 응답을 줬을 때 "다 끝났다"고 오판하면 안됨!!
     */
    private boolean hasPending(List<String> tokens, Map<String, JudgeExecutionResult> latest) {
        return tokens.stream().anyMatch(token -> {
            JudgeExecutionResult result = latest.get(token);
            return result == null || isPending(result.status());
        });
    }

    private boolean isPending(JudgeExecutionStatus status) {
        return status == JudgeExecutionStatus.IN_QUEUE || status == JudgeExecutionStatus.PROCESSING;
    }

    private boolean isSystemFailure(JudgeExecutionStatus status) {
        return status == JudgeExecutionStatus.INTERNAL_ERROR || status == JudgeExecutionStatus.UNKNOWN;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("검증용 실행 폴링이 인터럽트됨", e);
        }
    }
}