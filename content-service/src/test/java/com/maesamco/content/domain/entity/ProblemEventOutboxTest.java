package com.maesamco.content.domain.entity;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemEventOutboxTest {

    @Test
    @DisplayName("ProblemPublished Outbox를 생성하면 PENDING 상태와 초기 발행 정보를 가진다")
    void createPending_initializesPendingOutbox() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-21T00:00:00Z");

        String payload =
                """
                {
                  "eventType": "PROBLEM_PUBLISHED"
                }
                """;

        // when
        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        eventId,
                        problemId,
                        1,
                        payload,
                        occurredAt
                );

        // then
        assertThat(outbox.getEventId())
                .isEqualTo(eventId);

        assertThat(outbox.getAggregateType())
                .isEqualTo("PROBLEM");

        assertThat(outbox.getAggregateId())
                .isEqualTo(problemId);

        assertThat(outbox.getEventType())
                .isEqualTo("PROBLEM_PUBLISHED");

        assertThat(outbox.getEventVersion())
                .isEqualTo(1);

        assertThat(outbox.getPayload())
                .isEqualTo(payload);

        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isZero();

        assertThat(outbox.getOccurredAt())
                .isEqualTo(occurredAt);

        assertThat(outbox.getPublishedAt())
                .isNull();

        assertThat(outbox.getLastError())
                .isNull();
    }

    @Test
    @DisplayName("eventVersion이 1보다 작으면 Outbox를 생성할 수 없다")
    void createPending_invalidEventVersion_throwsException() {
        // when & then
        assertThatThrownBy(
                () -> ProblemEventOutbox.createPending(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        0,
                        "{}",
                        Instant.parse("2026-09-21T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventVersion must be greater than 0");
    }

    @Test
    @DisplayName("필수 값이 null이면 Outbox를 생성할 수 없다")
    void createPending_nullRequiredValue_throwsException() {
        // when & then
        assertThatThrownBy(
                () -> ProblemEventOutbox.createPending(
                        null,
                        UUID.randomUUID(),
                        1,
                        "{}",
                        Instant.parse("2026-09-21T00:00:00Z")
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventId must not be null");
    }

    @Test
    @DisplayName("Kafka 발행 실패를 기록하면 PENDING 상태를 유지하고 재시도 횟수가 증가한다")
    void recordFailure_incrementsRetryCountAndKeepsPending() {
        // given
        UUID eventId = UUID.randomUUID();

        ProblemEventOutbox outbox =
                createPendingOutbox(
                        eventId
                );

        // when
        outbox.recordFailure(
                "KAFKA_PUBLISH_FAILED",
                10
        );

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isEqualTo(1);

        assertThat(outbox.getLastError())
                .isEqualTo("KAFKA_PUBLISH_FAILED");

        assertThat(outbox.getEventId())
                .isEqualTo(eventId);

        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    @Test
    @DisplayName("Kafka 발행 실패가 최대 재시도 횟수에 도달하면 FAILED 상태로 전환된다")
    void recordFailure_changesStatusToFailed_whenMaxRetryCountReached() {
        // given
        ProblemEventOutbox outbox =
                createPendingOutbox();

        int maxRetryCount = 3;

        // when
        outbox.recordFailure(
                "KAFKA_PUBLISH_FAILED",
                maxRetryCount
        );

        outbox.recordFailure(
                "KAFKA_PUBLISH_FAILED",
                maxRetryCount
        );

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isEqualTo(2);

        // when
        outbox.recordFailure(
                "KAFKA_PUBLISH_FAILED",
                maxRetryCount
        );

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.FAILED);

        assertThat(outbox.getRetryCount())
                .isEqualTo(maxRetryCount);

        assertThat(outbox.getLastError())
                .isEqualTo("KAFKA_PUBLISH_FAILED");

        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    @Test
    @DisplayName("최대 재시도 횟수가 1보다 작으면 실패를 기록할 수 없다")
    void recordFailure_invalidMaxRetryCount_throwsException() {
        // given
        ProblemEventOutbox outbox =
                createPendingOutbox();

        // when & then
        assertThatThrownBy(
                () -> outbox.recordFailure(
                        "KAFKA_PUBLISH_FAILED",
                        0
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxRetryCount must be greater than 0");

        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isZero();
    }

    @Test
    @DisplayName("재시도로 복구할 수 없는 실패를 기록하면 FAILED 상태로 전환된다")
    void markFailed_changesStatusToFailed() {
        // given
        ProblemEventOutbox outbox =
                createPendingOutbox();

        // when
        outbox.markFailed(
                "EVENT_PAYLOAD_TOO_LARGE"
        );

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.FAILED);

        assertThat(outbox.getRetryCount())
                .isEqualTo(1);

        assertThat(outbox.getLastError())
                .isEqualTo("EVENT_PAYLOAD_TOO_LARGE");

        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    @Test
    @DisplayName("Kafka 발행에 성공하면 PUBLISHED 상태와 발행 시각이 기록된다")
    void markPublished_changesStatusAndRecordsPublishedAt() {
        // given
        ProblemEventOutbox outbox =
                createPendingOutbox();

        outbox.recordFailure(
                "KAFKA_PUBLISH_FAILED",
                10
        );

        Instant publishedAt =
                Instant.parse("2026-09-21T00:01:00Z");

        // when
        outbox.markPublished(
                publishedAt
        );

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.PUBLISHED);

        assertThat(outbox.getPublishedAt())
                .isEqualTo(publishedAt);

        assertThat(outbox.getLastError())
                .isNull();

        assertThat(outbox.getRetryCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Kafka 발행 성공 시 발행 시각이 null이면 예외가 발생한다")
    void markPublished_nullPublishedAt_throwsException() {
        // given
        ProblemEventOutbox outbox =
                createPendingOutbox();

        // when & then
        assertThatThrownBy(
                () -> outbox.markPublished(
                        null
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("publishedAt must not be null");

        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.PENDING);

        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    @Test
    @DisplayName("Kafka 발행 결과를 확정할 수 없으면 retryCount를 증가시키지 않고 PENDING 상태를 유지한다")
    void recordPostPublishFailure_keepsPendingWithoutIncreasingRetryCount() {
        // given
        ProblemEventOutbox outbox =
                createPendingOutbox();

        // when
        outbox.recordPostPublishFailure(
                "KAFKA_PUBLISH_OUTCOME_UNKNOWN"
        );

        // then
        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.PENDING);

        assertThat(outbox.getRetryCount())
                .isZero();

        assertThat(outbox.getLastError())
                .isEqualTo("KAFKA_PUBLISH_OUTCOME_UNKNOWN");

        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    @Test
    @DisplayName("PUBLISHED Outbox는 다시 발행 실패 상태로 변경할 수 없다")
    void recordFailure_publishedOutbox_throwsException() {
        // given
        ProblemEventOutbox outbox =
                createPendingOutbox();

        outbox.markPublished(
                Instant.parse("2026-09-21T00:01:00Z")
        );

        // when & then
        assertThatThrownBy(
                () -> outbox.recordFailure(
                        "KAFKA_PUBLISH_FAILED",
                        10
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only PENDING outbox can change publish state");

        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("FAILED Outbox는 다시 PUBLISHED 상태로 변경할 수 없다")
    void markPublished_failedOutbox_throwsException() {
        // given
        ProblemEventOutbox outbox =
                createPendingOutbox();

        outbox.markFailed(
                "EVENT_PAYLOAD_TOO_LARGE"
        );

        // when & then
        assertThatThrownBy(
                () -> outbox.markPublished(
                        Instant.parse("2026-09-21T00:01:00Z")
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only PENDING outbox can change publish state");

        assertThat(outbox.getStatus())
                .isEqualTo(ProblemEventOutboxStatus.FAILED);

        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    private ProblemEventOutbox createPendingOutbox() {
        return createPendingOutbox(
                UUID.randomUUID()
        );
    }

    private ProblemEventOutbox createPendingOutbox(
            UUID eventId
    ) {
        return ProblemEventOutbox.createPending(
                eventId,
                UUID.randomUUID(),
                1,
                "{}",
                Instant.parse("2026-09-21T00:00:00Z")
        );
    }
}