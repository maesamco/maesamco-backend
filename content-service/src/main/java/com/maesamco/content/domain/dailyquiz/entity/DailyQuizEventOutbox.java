package com.maesamco.content.domain.dailyquiz.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
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
import java.util.UUID;

/**
 * Daily Quiz 완료 이벤트의 Kafka 발행을 보장하기 위한 Outbox
 *
 * 마지막 문항 제출 트랜잭션 안에서 Daily Quiz Attempt의
 * COMPLETED 전이와 함께 PENDING 상태로 저장
 */
@Entity
@Table(
        name = "p_daily_quiz_event_outboxes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_quiz_event_outboxes_event_id",
                        columnNames = "event_id"
                ),
                @UniqueConstraint(
                        name = "uk_daily_quiz_event_outboxes_event_type_aggregate_id",
                        columnNames = {"event_type", "aggregate_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_daily_quiz_event_outboxes_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_daily_quiz_event_outboxes_status_occurred_at",
                        columnList = "status, occurred_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyQuizEventOutbox {

    private static final String AGGREGATE_TYPE_DAILY_QUIZ =
            "DAILY_QUIZ";

    private static final String EVENT_TYPE_DAILY_QUIZ_COMPLETED =
            "DAILY_QUIZ_COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DailyQuizEventOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    private DailyQuizEventOutbox(
            UUID eventId,
            UUID quizAttemptId,
            int eventVersion,
            String payload,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.aggregateType = AGGREGATE_TYPE_DAILY_QUIZ;
        this.aggregateId = quizAttemptId;
        this.eventType = EVENT_TYPE_DAILY_QUIZ_COMPLETED;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.status = DailyQuizEventOutboxStatus.PENDING;
        this.retryCount = 0;
        this.occurredAt = occurredAt;
        this.publishedAt = null;
        this.lastError = null;
    }

    /**
     * Kafka 발행 대기 상태의 DailyQuizCompleted Outbox를 생성
     */
    public static DailyQuizEventOutbox createPending(
            UUID eventId,
            UUID quizAttemptId,
            int eventVersion,
            String payload,
            Instant occurredAt
    ) {
        return new DailyQuizEventOutbox(
                requireId(eventId, "이벤트 ID"),
                requireId(quizAttemptId, "퀴즈 세트 ID"),
                requireEventVersion(eventVersion),
                requirePayload(payload),
                requireOccurredAt(occurredAt)
        );
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw internalError(fieldName + ": 필수입니다.");
        }
        return id;
    }

    private static int requireEventVersion(int eventVersion) {
        if (eventVersion < 1) {
            throw internalError("이벤트 버전은 1 이상이어야 합니다.");
        }
        return eventVersion;
    }

    private static String requirePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw internalError("이벤트 payload: 필수입니다.");
        }
        return payload;
    }

    private static Instant requireOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw internalError("이벤트 발생 시각: 필수입니다.");
        }
        return occurredAt;
    }

    private static BusinessException internalError(String message) {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, message);
    }
}
