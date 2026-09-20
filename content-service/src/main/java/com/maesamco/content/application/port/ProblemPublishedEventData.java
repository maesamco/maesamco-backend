package com.maesamco.content.application.port;

import com.maesamco.content.domain.entity.problem.ProblemVersion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ProblemPublished 이벤트 생성을 위해 Application 계층이 전달하는 데이터입니다.
 * Kafka 이벤트 DTO와 직렬화 방식은 Infrastructure 계층에서 결정합니다.
 */
public record ProblemPublishedEventData(
        UUID eventId,
        Instant occurredAt,
        UUID problemVersionId,
        ProblemVersion problemVersion
) {

    public ProblemPublishedEventData {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(problemVersionId, "problemVersionId must not be null");
        Objects.requireNonNull(problemVersion, "problemVersion must not be null");
    }
}