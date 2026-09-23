package com.maesamco.judge.infrastructure.adapter.judge0;

import static org.assertj.core.api.Assertions.assertThat;

import com.maesamco.judge.application.port.JudgeExecutionRequest;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import com.maesamco.judge.application.port.JudgeExecutionResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class Judge0ExecutionAdapterTest {

    @Nested
    @DisplayName("decode")
    class Decode {

        @Test
        @DisplayName("Base64 문자열을 원문으로 디코딩한다")
        void decodesBase64Value() {
            String encoded = java.util.Base64.getEncoder().encodeToString("Hello\nWorld".getBytes());

            String result = Judge0ExecutionAdapter.decode(encoded);

            assertThat(result).isEqualTo("Hello\nWorld");
        }

        @Test
        @DisplayName("null이면 null을 그대로 반환한다")
        void returnsNullForNull() {
            assertThat(Judge0ExecutionAdapter.decode(null)).isNull();
        }
    }

    @Nested
    @DisplayName("submitBatch — 실제 WebClient 요청 계약 검증")
    class SubmitBatch {

        private MockWebServer server;
        private Judge0ExecutionAdapter adapter;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
            WebClient webClient = WebClient.create(server.url("/").toString());
            adapter = new Judge0ExecutionAdapter(webClient);
        }

        @AfterEach
        void tearDown() throws IOException {
            server.shutdown();
        }

        @Test
        @DisplayName("POST /submissions/batch?base64_encoded=true로 요청하고, "
                + "source_code/stdin/expected_output이 Base64로 인코딩돼서 나간다")
        void sendsBase64EncodedBatchRequest() throws InterruptedException {
            // given
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("[{\"token\":\"tok-1\"}]"));

            JudgeExecutionRequest request = new JudgeExecutionRequest(
                    "public class Main {}", "1 2", "3", 2.0, 256000);

            // when
            List<String> tokens = adapter.submitBatch(List.of(request));

            // then
            assertThat(tokens).containsExactly("tok-1");

            RecordedRequest recorded = server.takeRequest();
            assertThat(recorded.getMethod()).isEqualTo("POST");
            assertThat(recorded.getPath()).startsWith("/submissions/batch?base64_encoded=true");

            String body = recorded.getBody().readUtf8();
            assertThat(body).contains(
                    "\"source_code\":\"" + Base64.getEncoder().encodeToString("public class Main {}".getBytes()) + "\"");
            assertThat(body).contains(
                    "\"stdin\":\"" + Base64.getEncoder().encodeToString("1 2".getBytes()) + "\"");
            assertThat(body).contains(
                    "\"expected_output\":\"" + Base64.getEncoder().encodeToString("3".getBytes()) + "\"");
            assertThat(body).contains("\"language_id\":62");
            assertThat(body).contains("\"cpu_time_limit\":2.0");
            assertThat(body).contains("\"memory_limit\":256000");
        }

        @Test
        @DisplayName("#292 — 요청이 20건 초과(21건)이면 Judge0에 20건+1건, 두 번으로 나눠 보낸다")
        void splitsRequestsExceedingJudge0BatchLimitIntoMultipleCalls() throws InterruptedException {
            // given — 21건 요청, Judge0 배치 제한(20)을 하나 넘김
            List<JudgeExecutionRequest> requests = java.util.stream.IntStream.range(0, 21)
                    .mapToObj(i -> new JudgeExecutionRequest("public class Main {}", "1 2", "3", 2.0, 256000))
                    .toList();

            String firstChunkTokens = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> "\"token\":\"tok-" + i + "\"")
                    .map(t -> "{" + t + "}")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(firstChunkTokens));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("[{\"token\":\"tok-20\"}]"));

            // when
            List<String> tokens = adapter.submitBatch(requests);

            // then — Judge0 호출이 정확히 2번 나가야 함(20 + 1), 전체 토큰은 21개 다 모여야 함
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertThat(tokens).hasSize(21);
            assertThat(tokens).doesNotContainNull();
        }

        @Test
        @DisplayName("#293 P1 — 두 청크 중 하나가 실패해도, 성공한 청크의 토큰은 유실되지 않고 "
                + "실패한 청크만 token=null로 채워진다(전체가 예외로 날아가지 않음)")
        void partialChunkFailureDoesNotLoseSuccessfulChunkTokens() {
            // given — 21건 요청 → 20건짜리 청크(성공) + 1건짜리 청크(실패, 500)
            List<JudgeExecutionRequest> requests = java.util.stream.IntStream.range(0, 21)
                    .mapToObj(i -> new JudgeExecutionRequest("public class Main {}", "1 2", "3", 2.0, 256000))
                    .toList();

            String firstChunkTokens = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> "\"token\":\"tok-" + i + "\"")
                    .map(t -> "{" + t + "}")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(firstChunkTokens));
            server.enqueue(new MockResponse().setResponseCode(500)); // 두 번째 청크(1건)는 실패

            // when
            List<String> tokens = adapter.submitBatch(requests);

            // then — 21개 전부 반환되되, 앞 20개는 성공 토큰, 마지막 1개만 null
            assertThat(tokens).hasSize(21);
            assertThat(tokens.subList(0, 20)).doesNotContainNull();
            assertThat(tokens.get(20)).isNull();
        }
    }

    @Nested
    @DisplayName("fetchResults — 배치 중 일부 파싱 실패해도 나머지는 살아남는다")
    class FetchResults {

        private MockWebServer server;
        private Judge0ExecutionAdapter adapter;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
            WebClient webClient = WebClient.create(server.url("/").toString());
            adapter = new Judge0ExecutionAdapter(webClient);
        }

        @AfterEach
        void tearDown() throws IOException {
            server.shutdown();
        }

        @Test
        @DisplayName("한 토큰의 status 필드가 없어 파싱 중 예외가 나도, "
                + "그 토큰은 사라지지 않고 INTERNAL_ERROR 결과로 반환된다")
        void returnsInternalErrorForItemThatFailsToParse() {
            // given
            String validStdout = Base64.getEncoder().encodeToString("3".getBytes());
            String body = "{"
                    + "\"submissions\": ["
                    + "  {\"token\":\"tok-ok\",\"stdout\":\"" + validStdout + "\",\"stderr\":null,"
                    + "   \"compile_output\":null,\"message\":null,\"time\":\"0.01\",\"memory\":1024,"
                    + "   \"status\":{\"id\":3,\"description\":\"Accepted\"}},"
                    + "  {\"token\":\"tok-bad\",\"stdout\":null,\"stderr\":null,"
                    + "   \"compile_output\":null,\"message\":null,\"time\":\"0.01\",\"memory\":1024,"
                    + "   \"status\":null}"
                    + "]}";
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(body));

            // when
            List<JudgeExecutionResult> results = adapter.fetchResults(List.of("tok-ok", "tok-bad"));

            // then — 두 건 다 살아남아야 함 (파싱 실패한 건도 사라지지 않음)
            assertThat(results).hasSize(2);

            JudgeExecutionResult ok = results.stream()
                    .filter(r -> r.token().equals("tok-ok")).findFirst().orElseThrow();
            assertThat(ok.stdout()).isEqualTo("3");
            assertThat(ok.status()).isEqualTo(com.maesamco.judge.application.port.JudgeExecutionStatus.ACCEPTED);

            JudgeExecutionResult bad = results.stream()
                    .filter(r -> r.token().equals("tok-bad")).findFirst().orElseThrow();
            assertThat(bad.status()).isEqualTo(com.maesamco.judge.application.port.JudgeExecutionStatus.INTERNAL_ERROR);
            assertThat(bad.stdout()).isNull();
        }

        @Test
        @DisplayName("#292 — 토큰이 20건 초과(21건)이면 Judge0에 두 번(20건+1건)으로 나눠 조회한다")
        void splitsTokensExceedingJudge0BatchLimitIntoMultipleCalls() {
            // given — 21개 토큰
            List<String> tokens = java.util.stream.IntStream.range(0, 21)
                    .mapToObj(i -> "tok-" + i)
                    .toList();

            String firstChunkBody = "{\"submissions\":" + java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> "{\"token\":\"tok-" + i + "\",\"stdout\":null,\"stderr\":null,"
                            + "\"compile_output\":null,\"message\":null,\"time\":null,\"memory\":null,"
                            + "\"status\":{\"id\":3,\"description\":\"Accepted\"}}")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]")) + "}";
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(firstChunkBody));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"submissions\":[{\"token\":\"tok-20\",\"stdout\":null,\"stderr\":null,"
                            + "\"compile_output\":null,\"message\":null,\"time\":null,\"memory\":null,"
                            + "\"status\":{\"id\":3,\"description\":\"Accepted\"}}]}"));

            // when
            List<JudgeExecutionResult> results = adapter.fetchResults(tokens);

            // then — Judge0 호출이 정확히 2번, 전체 21건 결과가 다 모여야 함
            assertThat(server.getRequestCount()).isEqualTo(2);
            assertThat(results).hasSize(21);
        }

        @Test
        @DisplayName("#292 — 한 청크 조회가 네트워크 오류로 실패해도 다른 청크 결과는 정상 반환된다")
        void continuesOtherChunksWhenOneChunkFetchFails() {
            // given — 21개 토큰, 첫 청크(20건)는 실패(500), 두 번째 청크(1건)는 성공
            List<String> tokens = java.util.stream.IntStream.range(0, 21)
                    .mapToObj(i -> "tok-" + i)
                    .toList();

            server.enqueue(new MockResponse().setResponseCode(500));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"submissions\":[{\"token\":\"tok-20\",\"stdout\":null,\"stderr\":null,"
                            + "\"compile_output\":null,\"message\":null,\"time\":null,\"memory\":null,"
                            + "\"status\":{\"id\":3,\"description\":\"Accepted\"}}]}"));

            // when
            List<JudgeExecutionResult> results = adapter.fetchResults(tokens);

            // then — 실패한 청크(20건)는 결과에서 빠지고, 성공한 청크(1건)만 반환됨
            // (예외로 전체가 죽지 않고, 실패 청크만 건너뛰고 계속 진행됨)
            assertThat(results).hasSize(1);
            assertThat(results.get(0).token()).isEqualTo("tok-20");
        }
    }
}