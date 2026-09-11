package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문제 이벤트 Outbox의 상태 변경 규칙을 검증합니다.
 */
class ProblemEventOutboxTest {

    @Test
    @DisplayName(
            "ProblemPublished Outbox를 생성하면 "
                    + "PENDING 상태와 초기 발행 정보를 가진다"
    )
    void createPending_initializesPendingOutbox() {
        UUID eventId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();

        Instant occurredAt =
                Instant.parse(
                        "2026-09-08T00:00:00Z"
                );

        String payload =
                """
                {
                  "eventType": "PROBLEM_PUBLISHED"
                }
                """;

        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        eventId,
                        problemId,
                        1,
                        payload,
                        occurredAt
                );

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
                .isEqualTo(
                        ProblemEventOutboxStatus.PENDING
                );

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
    @DisplayName(
            "Kafka 발행 실패를 기록하면 "
                    + "PENDING 상태를 유지하고 재시도 횟수가 증가한다"
    )
    void recordFailure_incrementsRetryCountAndKeepsPending() {
        UUID eventId = UUID.randomUUID();

        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        eventId,
                        UUID.randomUUID(),
                        1,
                        "{}",
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        outbox.recordFailure(
                "Kafka publish failed",
                10
        );

        assertThat(outbox.getStatus())
                .isEqualTo(
                        ProblemEventOutboxStatus.PENDING
                );

        assertThat(outbox.getRetryCount())
                .isEqualTo(1);

        assertThat(outbox.getLastError())
                .isEqualTo(
                        "Kafka publish failed"
                );

        /*
         * At-least-once 재전송에서도
         * 같은 이벤트 식별자를 계속 사용해야 합니다.
         */
        assertThat(outbox.getEventId())
                .isEqualTo(eventId);

        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    @Test
    @DisplayName(
            "Kafka 발행 실패가 최대 재시도 횟수에 도달하면 "
                    + "FAILED 상태로 전환된다"
    )
    void recordFailure_changesStatusToFailed_whenMaxRetryCountReached() {
        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "{}",
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        int maxRetryCount = 10;

        /*
         * 최대 횟수 직전까지는 재시도 가능한
         * PENDING 상태를 유지합니다.
         */
        for (int attempt = 1;
             attempt < maxRetryCount;
             attempt++) {

            outbox.recordFailure(
                    "KAFKA_PUBLISH_FAILED",
                    maxRetryCount
            );

            assertThat(outbox.getStatus())
                    .isEqualTo(
                            ProblemEventOutboxStatus.PENDING
                    );

            assertThat(outbox.getRetryCount())
                    .isEqualTo(attempt);
        }

        /*
         * 10번째 실패에서는 더 이상 PENDING으로 남지 않아
         * 오래된 실패 이벤트가 Relay 배치를 계속 점유하지 않습니다.
         */
        outbox.recordFailure(
                "KAFKA_PUBLISH_FAILED",
                maxRetryCount
        );

        assertThat(outbox.getStatus())
                .isEqualTo(
                        ProblemEventOutboxStatus.FAILED
                );

        assertThat(outbox.getRetryCount())
                .isEqualTo(maxRetryCount);

        assertThat(outbox.getLastError())
                .isEqualTo(
                        "KAFKA_PUBLISH_FAILED"
                );

        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    @Test
    @DisplayName(
            "영구 실패를 기록하면 FAILED 상태로 전환되어 재시도 대상에서 제외된다"
    )
    void markFailed_changesStatusToFailed() {
        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "{}",
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        outbox.markFailed(
                "EVENT_PAYLOAD_TOO_LARGE"
        );

        assertThat(outbox.getStatus())
                .isEqualTo(
                        ProblemEventOutboxStatus.FAILED
                );
        assertThat(outbox.getRetryCount())
                .isEqualTo(1);
        assertThat(outbox.getLastError())
                .isEqualTo(
                        "EVENT_PAYLOAD_TOO_LARGE"
                );
        assertThat(outbox.getPublishedAt())
                .isNull();
    }

    @Test
    @DisplayName(
            "Kafka 발행에 성공하면 "
                    + "PUBLISHED 상태와 발행 시각이 기록된다"
    )
    void markPublished_changesStatusAndRecordsPublishedAt() {
        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "{}",
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        outbox.recordFailure(
                "temporary failure",
                10
        );

        Instant publishedAt =
                Instant.parse(
                        "2026-09-08T00:01:00Z"
                );

        outbox.markPublished(
                publishedAt
        );

        assertThat(outbox.getStatus())
                .isEqualTo(
                        ProblemEventOutboxStatus.PUBLISHED
                );

        assertThat(outbox.getPublishedAt())
                .isEqualTo(publishedAt);

        /*
         * 성공한 Outbox에는 이전 실패 메시지를
         * 남기지 않습니다.
         */
        assertThat(outbox.getLastError())
                .isNull();

        /*
         * 과거에 한 번 실패했다는 사실은
         * retryCount에 그대로 남깁니다.
         */
        assertThat(outbox.getRetryCount())
                .isEqualTo(1);
    }
}
