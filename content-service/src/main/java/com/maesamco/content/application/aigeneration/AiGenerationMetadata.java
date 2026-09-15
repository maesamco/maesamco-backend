package com.maesamco.content.application.aigeneration;

import java.time.Instant;

public record AiGenerationMetadata(
        String modelName,
        String promptVersion,
        Instant calledAt,
        Integer responseTimeMs,
        Integer tokenUsage
) {
}
