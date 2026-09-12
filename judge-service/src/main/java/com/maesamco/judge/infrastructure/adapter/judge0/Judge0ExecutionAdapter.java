package com.maesamco.judge.infrastructure.adapter.judge0;

import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionRequest;
import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class Judge0ExecutionAdapter implements JudgeExecutionPort {

    // TODO: 원래 Java 17로 채점해야 하는데, 우리가 쓰는 Judge0 인스턴스에 Java17이 아직 정식으로 등록 안 돼있어서
    // 임시로 OpenJDK 13(id 62)으로 채점 중.
    private static final int JAVA_LANGUAGE_ID = 62;
    private static final String RESULT_FIELDS = "token,status,stdout,stderr,compile_output,time,memory,message";

    private final WebClient judge0WebClient;

    /**
     * Judge0 batch 제출 — 응답으로 오는 토큰 리스트는 요청 순서와 같다고 가정.
     *
     * ⚠️ 주의: Judge0 공식 문서(https://ce.judge0.com/)에는 POST /submissions/batch
     * 응답 순서가 요청 순서와 일치한다는 게 명시적으로 문서화돼있지 않음. 다만 Judge0가
     * 응답에 "이 토큰이 몇 번째 요청에 대한 거다"라는 상관관계 필드를 별도로 주지 않기 때문에,
     * 순서 매칭 외에는 요청-응답을 짝지을 방법이 없는 상황. 즉 이 API를 배치로 쓰는 이상
     * 사실상 암묵적으로 의존할 수밖에 없는 가정임. 만약 향후 이 가정이 깨지는 게
     * 확인되면(순서가 안 맞는 사례 발견), 배치 대신 건별 제출로 전환하는 것을 검토할 예정
     */
    @Override
    public List<String> submitBatch(List<JudgeExecutionRequest> requests) {
        List<Judge0SubmissionRequest> submissions = requests.stream()
                .map(this::toJudge0Request)
                .toList();

        List<Judge0TokenResponse> responses = judge0WebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/submissions/batch")
                        .queryParam("base64_encoded", true)
                        .build())
                .bodyValue(Judge0BatchSubmissionRequest.of(submissions))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<Judge0TokenResponse>>() {})
                .block();

        if (responses == null) {
            // Judge0 응답 자체가 없는 경우 — 로그만 남기고 상위(재시도 로직, 이슈 8번)에서 처리하도록 예외를 던짐
            log.warn("[Judge] Judge0 batch 제출 응답이 비어있음, 요청 건수={}", requests.size());
            throw new IllegalStateException("Judge0 batch submission returned no response");
        }

        return responses.stream()
                .map(Judge0TokenResponse::token) // 검증 실패 항목은 token이 null
                .collect(Collectors.toList());
    }

    @Override
    public List<JudgeExecutionResult> fetchResults(List<String> tokens) {
        String joinedTokens = String.join(",", tokens);

        Judge0BatchResultResponse response = judge0WebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/submissions/batch")
                        .queryParam("tokens", joinedTokens)
                        .queryParam("base64_encoded", true)
                        .queryParam("fields", RESULT_FIELDS)
                        .build())
                .retrieve()
                .bodyToMono(Judge0BatchResultResponse.class)
                .block();

        if (response == null || response.submissions() == null) {
            log.warn("[Judge] Judge0 batch 조회 응답이 비어있음, 토큰 개수={}", tokens.size());
            return List.of();
        }

        return response.submissions().stream()
                .map(this::toDomainResultSafely)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Judge0SubmissionRequest toJudge0Request(JudgeExecutionRequest request) {
        return Judge0SubmissionRequest.of(
                request.sourceCode(),
                JAVA_LANGUAGE_ID,
                request.stdin(),
                request.expectedOutput(),
                request.cpuTimeLimitSeconds(),
                request.memoryLimitKb()
        );
    }

    private JudgeExecutionResult toDomainResult(Judge0SubmissionResult result) {
        return new JudgeExecutionResult(
                result.token(),
                JudgeExecutionStatus.fromJudge0Id(result.status().id()),
                decode(result.stdout()),
                decode(result.stderr()),
                decode(result.compileOutput()),
                parseExecutionTimeMs(result.time()),
                result.memory()
        );
    }

    private JudgeExecutionResult toDomainResultSafely(Judge0SubmissionResult result) {
        try {
            return toDomainResult(result);
        } catch (Exception e) {
            log.error("[Judge] Judge0 결과 파싱 실패 — 이 건만 스킵하고 다음 폴링에서 재시도. token={}",
                    result.token(), e);
            return null;
        }
    }

    private Long parseExecutionTimeMs(String judge0TimeInSeconds) {
        if (judge0TimeInSeconds == null) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(judge0TimeInSeconds) * 1000);
        } catch (NumberFormatException e) {
            log.warn("[Judge] Judge0 time 파싱 실패, 원본값={}", judge0TimeInSeconds);
            return null;
        }
    }

    static String decode(String base64Value) {
        if (base64Value == null) {
            return null;
        }
        return new String(java.util.Base64.getMimeDecoder().decode(base64Value), java.nio.charset.StandardCharsets.UTF_8);
    }

}