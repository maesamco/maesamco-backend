package com.maesamco.judge.infrastructure.adapter.judge0;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Judge0Status(
        int id,
        String description
) {
}