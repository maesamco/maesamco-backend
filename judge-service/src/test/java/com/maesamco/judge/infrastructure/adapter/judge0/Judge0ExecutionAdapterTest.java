package com.maesamco.judge.infrastructure.adapter.judge0;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}