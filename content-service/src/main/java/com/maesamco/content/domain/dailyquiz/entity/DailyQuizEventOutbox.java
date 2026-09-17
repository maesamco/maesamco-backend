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
import jakarta.persistence.Version;
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

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    private DailyQuizEventOutbox(
            UUID eventId,
            UUID quizAttemptId,
            String eventType,
            int eventVersion,
            String payload,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.aggregateType = AGGREGATE_TYPE_DAILY_QUIZ;
        this.aggregateId = quizAttemptId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.status = DailyQuizEventOutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextAttemptAt = null;
        this.version = 0L;
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
            String eventType,
            int eventVersion,
            String payload,
            Instant occurredAt
    ) {
        return new DailyQuizEventOutbox(
                requireId(eventId, "이벤트 ID"),
                requireId(quizAttemptId, "퀴즈 세트 ID"),
                requireEventType(eventType),
                requireEventVersion(eventVersion),
                requirePayload(payload),
                requireOccurredAt(occurredAt)
        );
    }

    /**
     * Kafka 발행 성공 결과를 기록
     */
    public void recordPublishSuccess(
            Instant publishedAt
    ) {
        Instant validatedPublishedAt =
                requirePublishedAt(publishedAt);

        validatePendingStatus();

        this.status = DailyQuizEventOutboxStatus.PUBLISHED;
        this.publishedAt = validatedPublishedAt;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /**
     * 재시도 가능한 Kafka 발행 실패를 기록
     *
     * 실패 횟수가 최대 재시도 횟수에 도달하기 전까지는
     * PENDING 상태를 유지하고 다음 시도 시각을 기록합니다.
     * 최대 횟수에 도달하면 FAILED 상태로 전환합니다.
     *
     */
    public void recordPublishFailure(
            String error,
            // 최대 재시도 횟수
            int maxRetryCount,
            // 다음 발행 시도 가능 시각
            Instant nextAttemptAt
    ) {
        String validatedError = requireError(error);
        int validatedMaxRetryCount = requireMaxRetryCount(maxRetryCount);
        Instant validatedNextAttemptAt = requireNextAttemptAt(nextAttemptAt);

        validatePendingStatus();

        this.retryCount++;
        this.lastError = validatedError;
        this.publishedAt = null;

        if (this.retryCount >= validatedMaxRetryCount) {
            this.status = DailyQuizEventOutboxStatus.FAILED;
            this.nextAttemptAt = null;
            return;
        }

        this.nextAttemptAt = validatedNextAttemptAt;
    }

    /**
     * Kafka 발행 결과를 확인하지 못한 시도를 기록
     *
     * ACK 대기 시간 초과처럼 이벤트가 이미 전달되었을 가능성이 있는 경우에는
     * 재시도 횟수와 다음 시도 시각만 기록하고 FAILED 상태로 종료하지 않습니다.
     */
    public void recordPublishOutcomeUnknown(
            String error,
            Instant nextAttemptAt
    ) {
        String validatedError = requireError(error);
        Instant validatedNextAttemptAt = requireNextAttemptAt(nextAttemptAt);

        validatePendingStatus();

        this.retryCount++;
        this.lastError = validatedError;
        this.nextAttemptAt = validatedNextAttemptAt;
        this.publishedAt = null;
    }

    /**
     * 재시도로 복구할 수 없는 Kafka 발행 실패를 기록
     */
    public void recordUnrecoverablePublishFailure(
            String error
    ) {
        String validatedError = requireError(error);

        validatePendingStatus();

        this.status = DailyQuizEventOutboxStatus.FAILED;
        this.retryCount++;
        this.nextAttemptAt = null;
        this.publishedAt = null;
        this.lastError = validatedError;
    }

    private static String requireEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이벤트 타입은 필수입니다.");
        }
        return eventType;
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldName + "는 필수입니다.");
        }
        return id;
    }

    private static int requireEventVersion(int eventVersion) {
        if (eventVersion < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이벤트 버전은 1 이상이어야 합니다.");
        }
        return eventVersion;
    }

    private static String requirePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이벤트 본문은 필수입니다.");
        }
        return payload;
    }

    private static Instant requireOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이벤트 발생 시각은 필수입니다.");
        }
        return occurredAt;
    }

    private static Instant requirePublishedAt(Instant publishedAt) {
        if (publishedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "발행 완료 시각은 필수입니다.");
        }
        return publishedAt;
    }

    private static Instant requireNextAttemptAt(Instant nextAttemptAt) {
        if (nextAttemptAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "다음 발행 시도 시각은 필수입니다.");
        }
        return nextAttemptAt;
    }

    private static String requireError(String error) {
        if (error == null || error.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "발행 실패 사유는 필수입니다.");
        }
        return error;
    }

    private static int requireMaxRetryCount(int maxRetryCount) {
        if (maxRetryCount < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최대 재시도 횟수는 1 이상이어야 합니다.");
        }
        return maxRetryCount;
    }

    private void validatePendingStatus() {
        if (this.status != DailyQuizEventOutboxStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 Outbox만 변경할 수 있습니다.");
        }
    }
}
