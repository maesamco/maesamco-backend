package com.maesamco.judge.domain.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Outbox 패턴 레코드 — Submission 관련 상태 변경(접수, 채점 완료)과 이벤트 발행을
 * 하나의 트랜잭션으로 묶기 위한 아웃박스 테이블.
 *
 * 이 Outbox는 특정 이벤트 전용이 아니라 Submission 애그리거트에 대한 발행 대기함입니다.
 *
 * 발생한 사실을 기록하고, 수정·삭제 대상은 아닙니다. Relay가 발행 성공 여부를 마킹하는
 * markPublished()만 상태를 바꿉니다.
 */
@Entity
@Table(
        name = "p_submission_event_outboxes",
        indexes = @Index(name = "idx_submission_event_outboxes_status", columnList = "status")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionEventOutbox {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    // "JudgeRequested" / "SubmissionJudged"
    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** 현재 선점을 식별하는 fencing token(#272). IN_PROGRESS일 때만 값이 있다. */
    @Column(name = "claim_id")
    private UUID claimId;

    /** 이 선점이 만료되는 시각(#272). 지나면 다른 Worker가 재선점할 수 있다. */
    @Column(name = "lease_until")
    private Instant leaseUntil;

    /** claim_id 검증을 통과한 뒤의 쓰기 충돌을 막는 2차 방어(낙관적 락). */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private SubmissionEventOutbox(UUID aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attemptCount = 0;
    }

    /** Submission 관련 트랜잭션(접수 또는 채점 완료) 안에서 같은 트랜잭션으로 저장 */
    public static SubmissionEventOutbox create(UUID aggregateId, String eventType, String payload) {
        return new SubmissionEventOutbox(aggregateId, eventType, payload);
    }

    /**
     * Relay Worker가 Kafka 발행을 시작하기 위해 이 Outbox를 선점한다(#272).
     * PENDING이거나 lease가 만료된 IN_PROGRESS 행만 선점할 수 있다. 호출자는
     * {@code SELECT ... FOR UPDATE SKIP LOCKED}로 잠근 행에만 호출해야 한다 — 상태 검증만으로는
     * 진짜 동시 선점을 막지 못한다.
     */
    public void claimForPublish(UUID claimId, Instant claimedAt, Instant leaseUntil) {
        if (claimId == null || claimedAt == null || leaseUntil == null) {
            throw new IllegalArgumentException("선점 ID와 선점/만료 시각은 필수입니다.");
        }
        if (!leaseUntil.isAfter(claimedAt)) {
            throw new IllegalArgumentException("선점 만료 시각은 선점 시각 이후여야 합니다.");
        }
        boolean pending = this.status == OutboxStatus.PENDING;
        boolean expiredClaim = this.status == OutboxStatus.IN_PROGRESS
                && this.leaseUntil != null && !this.leaseUntil.isAfter(claimedAt);
        if (!pending && !expiredClaim) {
            throw new IllegalStateException("선점 가능한 상태가 아닙니다. status=" + this.status);
        }
        this.status = OutboxStatus.IN_PROGRESS;
        this.claimId = claimId;
        this.leaseUntil = leaseUntil;
    }

    /** 이 호출자가 지금 유효한 선점(IN_PROGRESS + 같은 claimId)을 들고 있는지. lease 만료 후 재선점됐다면 false. */
    public boolean isClaimedBy(UUID claimId) {
        return this.status == OutboxStatus.IN_PROGRESS && claimId != null && claimId.equals(this.claimId);
    }

    /** 발행을 이어가지 못했을 때 선점을 풀어 PENDING으로 되돌린다 — 다음 폴링에서 다시 선점된다. */
    public void releaseClaim() {
        this.status = OutboxStatus.PENDING;
        clearClaim();
    }

    private void clearClaim() {
        this.claimId = null;
        this.leaseUntil = null;
    }

    /** Relay가 Kafka 발행 시도(성공/실패 무관)마다 호출 — 재시도 상한 판단용. */
    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    /** Outbox Relay가 Kafka 발행에 성공했을 때 호출. */
    public void markPublished() {
        this.status = OutboxStatus.COMPLETED;
        this.processedAt = Instant.now();
        clearClaim();
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.processedAt = Instant.now();
        clearClaim();
    }
}