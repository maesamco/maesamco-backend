package com.maesamco.judge.infrastructure.adapter.judge0;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Judge0 POST /submissions/batch 요청 바디 안, submissions 배열의 항목 하나.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Judge0SubmissionRequest(
        @JsonProperty("source_code") String sourceCode,
        @JsonProperty("language_id") int languageId,
        String stdin,
        @JsonProperty("expected_output") String expectedOutput,
        @JsonProperty("cpu_time_limit") Double cpuTimeLimit,
        @JsonProperty("memory_limit") Integer memoryLimit
) {
    public static Judge0SubmissionRequest of(
            String sourceCode,
            int languageId,
            String stdin,
            String expectedOutput,
            double cpuTimeLimitSeconds,
            int memoryLimitKb
    ) {
        return new Judge0SubmissionRequest(
                sourceCode, languageId, stdin, expectedOutput,
                cpuTimeLimitSeconds, memoryLimitKb
        );
    }
}