package com.maesamco.content.domain.entity.problem;

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

import java.time.Duration;
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
 *
 * <p>다중 인스턴스 환경에서 같은 Outbox가 중복 발행되지 않도록
 * Relay는 발행 직전에 {@link #claim(UUID, Instant, Instant)}로 행을 선점합니다.
 * 선점된 행은 IN_PROGRESS 상태가 되며, 발행 결과는 선점을 보유한
 * Worker(claimId 일치)만 기록할 수 있습니다.
 * lease가 만료된 IN_PROGRESS 행은 다른 Worker가 재선점할 수 있습니다.</p>
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
                        name = "idx_problem_event_outboxes_status_occurred_at_id",
                        columnList = "status, occurred_at, id"
                ),
                @Index(
                        name = "idx_problem_event_outboxes_pollable",
                        columnList = "status, next_attempt_at, occurred_at, id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemEventOutbox {

    private static final String AGGREGATE_TYPE_PROBLEM = "PROBLEM";
    private static final String EVENT_TYPE_PROBLEM_PUBLISHED = "PROBLEM_PUBLISHED";

    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(30);

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
    private ProblemEventOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "claim_id")
    private UUID claimId;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

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
        this.nextAttemptAt = null;
        this.lastError = null;
        this.leaseUntil = null;
        this.claimId = null;
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
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(problemId, "problemId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");

        if (eventVersion < 1) {
            throw new IllegalArgumentException(
                    "eventVersion must be greater than 0"
            );
        }

        return new ProblemEventOutbox(
                eventId,
                problemId,
                eventVersion,
                payload,
                occurredAt
        );
    }

    /**
     * Relay Worker가 Kafka 발행을 시작하기 전에 Outbox를 선점합니다.
     *
     * <p>재시도 시각이 도래한 PENDING 행, 또는 lease가 만료된 IN_PROGRESS 행만
     * 선점할 수 있습니다. lease가 만료된 행은 이전 Worker가 종료되었거나
     * 응답하지 않는 것으로 보고 새 claimId로 처리권을 넘겨받습니다.</p>
     *
     * @param claimId 이번 발행 시도를 식별하는 선점 ID
     * @param claimedAt 선점 시각
     * @param leaseUntil 선점 만료 시각
     */
    public void claim(UUID claimId, Instant claimedAt, Instant leaseUntil) {
        Objects.requireNonNull(claimId, "claimId must not be null");
        Objects.requireNonNull(claimedAt, "claimedAt must not be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");

        if (!leaseUntil.isAfter(claimedAt)) {
            throw new IllegalArgumentException(
                    "leaseUntil must be after claimedAt"
            );
        }

        if (!isClaimable(claimedAt)) {
            throw new IllegalStateException(
                    "Only due PENDING or lease-expired IN_PROGRESS outbox can be claimed"
            );
        }

        this.status = ProblemEventOutboxStatus.IN_PROGRESS;
        this.claimId = claimId;
        this.leaseUntil = leaseUntil;
        this.nextAttemptAt = null;
    }

    /**
     * 주어진 claimId가 현재 유효한 선점인지 확인합니다.
     * lease 만료 후 다른 Worker가 재선점했다면 false입니다.
     */
    public boolean isClaimedBy(UUID claimId) {
        return this.status == ProblemEventOutboxStatus.IN_PROGRESS
                && claimId != null
                && claimId.equals(this.claimId);
    }

    /**
     * Kafka 발행 실패를 기록합니다.
     *
     * <p>최대 재시도 횟수에 도달하기 전까지는 PENDING 상태로 되돌리고,
     * 지수 백오프로 다음 재시도 가능 시각을 기록합니다.</p>
     *
     * @param claimId 현재 선점 ID
     * @param error 외부 노출이 없는 안전한 오류 요약
     * @param maxRetryCount 최대 재시도 횟수
     */
    public void recordFailure(UUID claimId, String error, int maxRetryCount) {
        if (maxRetryCount < 1) {
            throw new IllegalArgumentException(
                    "maxRetryCount must be greater than 0"
            );
        }

        validateActiveClaim(claimId);

        this.retryCount++;
        this.lastError = error;
        this.publishedAt = null;
        clearClaim();

        if (this.retryCount >= maxRetryCount) {
            this.status = ProblemEventOutboxStatus.FAILED;
            this.nextAttemptAt = null;
            return;
        }

        this.status = ProblemEventOutboxStatus.PENDING;
        scheduleNextAttempt();
    }

    /**
     * 재시도로 복구할 수 없는 Kafka 발행 실패를 기록합니다.
     *
     * @param claimId 현재 선점 ID
     * @param error 외부 노출이 없는 안전한 오류 요약
     */
    public void markFailed(UUID claimId, String error) {
        validateActiveClaim(claimId);

        this.status = ProblemEventOutboxStatus.FAILED;
        this.retryCount++;
        this.publishedAt = null;
        this.nextAttemptAt = null;
        this.lastError = error;
        clearClaim();
    }

    /**
     * Kafka 발행 성공을 기록합니다.
     *
     * @param claimId 현재 선점 ID
     * @param publishedAt 실제 Kafka 발행 완료 시각
     */
    public void markPublished(UUID claimId, Instant publishedAt) {
        validateActiveClaim(claimId);
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");

        this.status = ProblemEventOutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.nextAttemptAt = null;
        this.lastError = null;
        clearClaim();
    }

    /**
     * Kafka 발행 결과를 확정할 수 없는 실패를 기록합니다.
     * FAILED 상태로 종료하지 않고 PENDING 상태로 되돌려 재시도합니다.
     *
     * @param claimId 현재 선점 ID
     * @param error 외부 노출이 없는 안전한 오류 요약
     */
    public void recordPostPublishFailure(UUID claimId, String error) {
        validateActiveClaim(claimId);

        this.retryCount++;
        this.lastError = error;
        this.status = ProblemEventOutboxStatus.PENDING;
        clearClaim();
        scheduleNextAttempt();
    }

    /**
     * 실패한 시도 이후 지수 백오프로 다음 재시도 가능 시각을 기록합니다.
     * 최대 지연 시간은 30분입니다.
     */
    private void scheduleNextAttempt() {
        long backoffSeconds = Math.min(
                BASE_RETRY_DELAY.toSeconds() * (1L << Math.min(this.retryCount, 20)),
                MAX_RETRY_DELAY.toSeconds()
        );

        this.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds);
    }

    private boolean isClaimable(Instant claimedAt) {
        boolean duePending = this.status == ProblemEventOutboxStatus.PENDING
                && (this.nextAttemptAt == null || !this.nextAttemptAt.isAfter(claimedAt));

        boolean expiredClaim = this.status == ProblemEventOutboxStatus.IN_PROGRESS
                && this.leaseUntil != null
                && !this.leaseUntil.isAfter(claimedAt);

        return duePending || expiredClaim;
    }

    /**
     * 발행 결과 기록은 현재 선점을 보유한 Worker만 허용합니다.
     */
    private void validateActiveClaim(UUID claimId) {
        if (!isClaimedBy(claimId)) {
            throw new IllegalStateException(
                    "Only the worker holding the active claim can change publish state"
            );
        }
    }

    private void clearClaim() {
        this.claimId = null;
        this.leaseUntil = null;
    }
}
