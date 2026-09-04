package com.maesamco.content.aigeneration.application;

import java.time.Instant;

public record AiGenerationMetadata(
        String modelName,
        String promptVersion,
        Instant calledAt,
        Integer responseTimeMs,
        Integer tokenUsage
) {
}
