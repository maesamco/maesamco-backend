package com.maesamco.judge.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Judge API가 채점 요청 시 발행하는 이벤트 (Outbox Relay → Judge Worker 소비).
 */
public record JudgeRequestedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID submissionId
) {

    public static JudgeRequestedEvent of(UUID submissionId) {
        return new JudgeRequestedEvent(
                UUID.randomUUID(),
                "JudgeRequested",
                1,
                Instant.now(),
                submissionId
        );
    }
}