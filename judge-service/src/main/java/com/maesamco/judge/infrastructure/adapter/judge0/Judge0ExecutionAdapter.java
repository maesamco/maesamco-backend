package com.maesamco.judge.infrastructure.adapter.judge0;

import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.application.port.JudgeExecutionRequest;
import com.maesamco.judge.application.port.JudgeExecutionResult;
import com.maesamco.judge.application.port.JudgeExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
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

    // ⚠️ 실배포 중 발견(#292) — Judge0의 배치 크기 제한(judge0.conf의
    // MAX_SUBMISSION_BATCH_SIZE, 기본값 20)을 넘겨 한 번에 요청하면 Judge0가
    // 요청 검증 단계에서 즉시 400을 반환한다. submitBatch(제출)와 fetchResults(폴링)
    // 둘 다 이 제한 아래에서 여러 번 나눠 호출하도록 청크 분할한다. 이 값은
    // judge0.conf의 MAX_SUBMISSION_BATCH_SIZE와 반드시 일치해야 한다(둘 다 20으로
    // 명시 설정, 암묵적 기본값 의존 제거).
    @Value("${judge.max-batch-size:20}")
    // ⚠️ 리뷰 반영 — @Value의 ${...:20}은 Spring이 프로퍼티를 못 찾을 때만 쓰이는
    // 기본값이라, 이 프로젝트 관례대로 테스트에서 new Judge0ExecutionAdapter(webClient)로
    // Spring 컨테이너 없이 직접 생성하면 전혀 적용되지 않고 int 기본값 0으로 남는다.
    // partition(list, 0)은 i += 0으로 루프가 끝나지 않아 빈 리스트를 무한히 쌓다가
    // OOM으로 죽는다(실제로 기존 Judge0ExecutionAdapterTest에서 재현됨). 필드 선언에
    // 리터럴 기본값을 직접 줘서, Spring 없이 생성돼도 안전하도록 한다.
    private int judge0MaxBatchSize = 20;

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
        List<String> tokens = new ArrayList<>(requests.size());
        for (List<JudgeExecutionRequest> chunk : partition(requests, judge0MaxBatchSize)) {
            tokens.addAll(submitSingleChunk(chunk));
        }
        return tokens;
    }

    private List<String> submitSingleChunk(List<JudgeExecutionRequest> requests) {
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
        List<JudgeExecutionResult> results = new ArrayList<>(tokens.size());
        for (List<String> chunk : partition(tokens, judge0MaxBatchSize)) {
            // ⚠️ #292 — 청크 하나가 실패해도(네트워크 오류 등) 나머지 청크는 계속 진행한다.
            // 예전엔 한 번의 GET 호출이 통째로 실패하면 폴링 대상 전체(최대 100건)가
            // 이번 주기에서 전부 버려지고, 다음 주기도 같은(오래된) 항목부터 다시 시도하며
            // 영구적으로 반영이 정지되는 구조였다.
            try {
                results.addAll(fetchSingleChunk(chunk));
            } catch (Exception ex) {
                log.error("[Judge] Judge0 결과 폴링 청크 실패 — 이 청크만 건너뛰고 계속 진행. 토큰 개수={}",
                        chunk.size(), ex);
            }
        }
        return results;
    }

    private List<JudgeExecutionResult> fetchSingleChunk(List<String> tokens) {
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
                .toList();
    }

    /**
     * 리스트를 size 단위로 나눈다. 외부 라이브러리(Guava 등) 없이 표준 라이브러리만으로 구현.
     *
     * ⚠️ 리뷰로 발견된 문제 재발 방지용 방어 코드 — size가 0 이하로 잘못 들어오면(설정
     * 주입 실패, 향후 다른 호출부의 실수 등) i += size가 전혀 진행되지 않아 무한 루프로
     * OOM을 일으킨다. 필드 기본값(judge0MaxBatchSize = 20)으로 이미 막았지만, 이 메서드
     * 자체도 잘못된 입력에 안전하도록 명시적으로 예외를 던진다.
     */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("partition size는 1 이상이어야 합니다. size=" + size);
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
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
            log.error("[Judge] Judge0 결과 파싱 실패 — 인프라 오류로 명시 처리. token={}", result.token(), e);
            return new JudgeExecutionResult(
                    result.token(),
                    JudgeExecutionStatus.INTERNAL_ERROR,
                    null, null, null, null, null);
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