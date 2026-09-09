package com.maesamco.judge.infrastructure.adapter.judge0;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Judge0SubmissionResult(
        String token,
        String stdout,
        String stderr,
        @JsonProperty("compile_output") String compileOutput,
        String message,
        String time,
        Integer memory,
        Judge0Status status
) {
}