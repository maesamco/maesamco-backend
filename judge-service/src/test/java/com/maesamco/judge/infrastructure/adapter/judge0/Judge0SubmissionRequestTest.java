package com.maesamco.judge.infrastructure.adapter.judge0;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class Judge0SubmissionRequestTest {

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("sourceCode/stdin/expectedOutput을 Base64로 인코딩해서 담는다")
        void encodesTextFields() {
            String sourceCode = "public class Main { }";
            String stdin = "1 2";
            String expectedOutput = "3";

            Judge0SubmissionRequest request = Judge0SubmissionRequest.of(
                    sourceCode, 62, stdin, expectedOutput, 2.0, 65536
            );

            assertThat(decode(request.sourceCode())).isEqualTo(sourceCode);
            assertThat(decode(request.stdin())).isEqualTo(stdin);
            assertThat(decode(request.expectedOutput())).isEqualTo(expectedOutput);
        }

        @Test
        @DisplayName("stdin이 null이면 인코딩하지 않고 null 그대로 둔다")
        void keepsNullStdinAsNull() {
            Judge0SubmissionRequest request = Judge0SubmissionRequest.of(
                    "public class Main { }", 62, null, "3", 2.0, 65536
            );

            assertThat(request.stdin()).isNull();
        }

        private String decode(String base64Value) {
            return new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
        }
    }
}