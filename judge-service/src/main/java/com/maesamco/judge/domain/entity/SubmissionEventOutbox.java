package com.maesamco.judge.domain.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

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

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "JSONB")
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

    /** Relay가 Kafka 발행 시도(성공/실패 무관)마다 호출 — 재시도 상한 판단용. */
    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    /** Outbox Relay가 Kafka 발행에 성공했을 때 호출. */
    public void markPublished() {
        this.status = OutboxStatus.COMPLETED;
        this.processedAt = Instant.now();
    }
}