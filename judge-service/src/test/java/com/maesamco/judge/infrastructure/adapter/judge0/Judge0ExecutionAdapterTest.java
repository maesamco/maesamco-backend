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
        @DisplayName("한 토큰의 status 필드가 없어 파싱 중 예외가 나도, 나머지 토큰의 결과는 정상적으로 반환된다")
        void skipsOnlyTheItemThatFailsToParse() {
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

            // then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).token()).isEqualTo("tok-ok");
            assertThat(results.get(0).stdout()).isEqualTo("3");
        }
    }
}