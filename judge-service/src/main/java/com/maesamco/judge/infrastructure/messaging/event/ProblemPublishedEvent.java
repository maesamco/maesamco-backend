package com.maesamco.judge.infrastructure.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Content Service가 발행하는 ProblemPublished 이벤트의 역직렬화 대상 DTO.
 */
public record ProblemPublishedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID problemId,
        UUID problemVersionId,
        String language,
        String starterCode,
        List<TestCaseItem> testCases,
        int timeLimit,
        int memoryLimit,
        Instant publishedAt
) {

    public record TestCaseItem(
            UUID testCaseId,
            boolean isPublic,
            String input,
            String expectedOutput,
            int displayOrder
    ) {
    }
}
