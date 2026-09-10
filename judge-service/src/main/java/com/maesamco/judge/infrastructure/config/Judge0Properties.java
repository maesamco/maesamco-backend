package com.maesamco.judge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "judge0")
public record Judge0Properties(
        String baseUrl,
        int connectTimeoutMs,
        int responseTimeoutMs
) {
}