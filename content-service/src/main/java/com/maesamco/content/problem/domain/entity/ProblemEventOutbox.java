package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Problem 도메인에서 발생한 이벤트의 Kafka 발행을 보장하기 위한 Outbox입니다.
 *
 * <p>문제 발행 승인 트랜잭션 안에서 ProblemVersion,
 * Problem 상태 변경과 함께 저장됩니다.</p>
 *
 * <p>Kafka 발행에 실패하더라도 Outbox는 PENDING 상태로 남으며,
 * 동일한 eventId를 유지한 채 Relay가 다시 발행을 시도합니다.</p>
 */
@Entity
@Table(
        name = "p_problem_event_outboxes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_problem_event_outboxes_event_id",
                        columnNames = "event_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_problem_event_outboxes_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_problem_event_outboxes_status_occurred_at",
                        columnList = "status, occurred_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemEventOutbox {

    private static final String AGGREGATE_TYPE_PROBLEM =
            "PROBLEM";

    private static final String EVENT_TYPE_PROBLEM_PUBLISHED =
            "PROBLEM_PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "event_id",
            nullable = false,
            updatable = false
    )
    private UUID eventId;

    @Column(
            name = "aggregate_type",
            nullable = false,
            updatable = false,
            length = 50
    )
    private String aggregateType;

    @Column(
            name = "aggregate_id",
            nullable = false,
            updatable = false
    )
    private UUID aggregateId;

    @Column(
            name = "event_type",
            nullable = false,
            updatable = false,
            length = 50
    )
    private String eventType;

    @Column(
            name = "event_version",
            nullable = false,
            updatable = false
    )
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "payload",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private ProblemEventOutboxStatus status;

    @Column(
            name = "retry_count",
            nullable = false
    )
    private int retryCount;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private Instant occurredAt;

    @Column(
            name = "published_at"
    )
    private Instant publishedAt;

    @Column(
            name = "last_error",
            columnDefinition = "text"
    )
    private String lastError;

    private ProblemEventOutbox(
            UUID eventId,
            UUID aggregateId,
            int eventVersion,
            String payload,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.aggregateType = AGGREGATE_TYPE_PROBLEM;
        this.aggregateId = aggregateId;
        this.eventType = EVENT_TYPE_PROBLEM_PUBLISHED;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.status = ProblemEventOutboxStatus.PENDING;
        this.retryCount = 0;
        this.occurredAt = occurredAt;
        this.publishedAt = null;
        this.lastError = null;
    }

    /**
     * Kafka 발행 대기 상태의 ProblemPublished Outbox를 생성합니다.
     *
     * @param eventId 이벤트 고유 식별자
     * @param problemId 문제 식별자
     * @param eventVersion 이벤트 스키마 버전
     * @param payload 직렬화된 ProblemPublished 이벤트 JSON
     * @param occurredAt 이벤트 발생 시각
     * @return PENDING 상태의 Outbox
     */
    public static ProblemEventOutbox createPending(
            UUID eventId,
            UUID problemId,
            int eventVersion,
            String payload,
            Instant occurredAt
    ) {
        Objects.requireNonNull(
                eventId,
                "eventId must not be null"
        );

        Objects.requireNonNull(
                problemId,
                "problemId must not be null"
        );

        Objects.requireNonNull(
                payload,
                "payload must not be null"
        );

        Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
        );

        return new ProblemEventOutbox(
                eventId,
                problemId,
                eventVersion,
                payload,
                occurredAt
        );
    }

    /**
     * Kafka 발행 실패를 기록합니다.
     *
     * <p>실패 횟수가 최대 재시도 횟수에 도달하기 전까지는
     * PENDING 상태를 유지하여 동일 eventId로 다시 발행할 수 있습니다.</p>
     *
     * <p>최대 재시도 횟수에 도달하면 FAILED 상태로 전환하여
     * 오래된 실패 이벤트가 Relay의 PENDING 배치를 계속 점유하지 않도록 합니다.</p>
     *
     * @param error 외부 노출이 없는 안전한 오류 요약
     * @param maxRetryCount 최대 재시도 횟수
     */
    public void recordFailure(
            String error,
            int maxRetryCount
    ) {
        if (maxRetryCount < 1) {
            throw new IllegalArgumentException(
                    "maxRetryCount must be greater than 0"
            );
        }

        this.retryCount++;
        this.lastError = error;

        if (this.retryCount >= maxRetryCount) {
            this.status =
                    ProblemEventOutboxStatus.FAILED;
            this.publishedAt = null;
        }
    }

    /**
     * 재시도로 복구할 수 없는 Kafka 발행 실패를 기록합니다.
     *
     * <p>FAILED 상태는 Relay의 PENDING 조회 대상에서 제외되어
     * 이후 정상 이벤트의 발행을 막지 않습니다.</p>
     *
     * @param error 외부 노출이 없는 안전한 오류 요약
     */
    public void markFailed(String error) {
        this.status = ProblemEventOutboxStatus.FAILED;
        this.retryCount++;
        this.publishedAt = null;
        this.lastError = error;
    }

    /**
     * Kafka 발행 성공을 기록합니다.
     *
     * @param publishedAt 실제 Kafka 발행 완료 시각
     */
    public void markPublished(
            Instant publishedAt
    ) {
        this.status = ProblemEventOutboxStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(
                publishedAt,
                "publishedAt must not be null"
        );
        this.lastError = null;
    }
}
