package com.maesamco.judge.infrastructure.adapter.judge0;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Judge0BatchResultResponse(
        List<Judge0SubmissionResult> submissions
) {
}