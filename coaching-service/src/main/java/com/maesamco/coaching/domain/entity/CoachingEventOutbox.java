package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.util.Validate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox 패턴 레코드 — 이슈 #51(코칭 세션 완료 처리)과 이벤트 발행(`CoachingCompleted`)을
 * 하나의 트랜잭션으로 묶기 위한 아웃박스 테이블. Judge Service의
 * `p_submission_event_outboxes`(이슈 #63)와 동일한 설계다.
 *
 * 이 Outbox는 `CoachingCompleted` 전용이 아니라 `CoachingSession` 애그리거트에 대한 발행
 * 대기함이다 — 나중에 다른 코칭 이벤트가 생기면 같은 테이블에 event_type만 늘려서 쓴다.
 *
 * 이슈 #51은 이 엔티티를 만들고 저장하는 것까지만 담당한다(완료 처리와 같은 트랜잭션).
 * 실제로 폴링해서 Kafka에 발행하는 Relay Worker는 이슈 #89에서 별도로 구현한다 —
 * `markPublished()`/`incrementAttemptCount()`는 그때 Relay가 쓸 메서드를 미리 정의해둔
 * 것뿐, 지금은 어디서도 호출되지 않는다.
 *
 * payload는 AiFeedback과 동일한 이유로 JsonNode + `@JdbcTypeCode(SqlTypes.JSON)`을 쓴다
 * (Judge Service는 String + columnDefinition="jsonb"를 쓰지만, coaching-service에선 이미
 * Testcontainers로 왕복 검증까지 마친 JsonNode 패턴이 있어 그걸 그대로 따른다).
 *
 * 발생한 사실을 기록하고, 수정·삭제 대상은 아니다(팀 컨벤션 16절, append-only Outbox
 * 계열) — `markPublished()`만 상태를 바꾼다.
 */
@Entity
@Table(
        name = "p_coaching_event_outboxes",
        indexes = @Index(name = "idx_coaching_event_outboxes_status", columnList = "status")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CoachingEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** 지금은 "CoachingCompleted" 하나뿐 — Flyway V8 baseline의 CHECK 제약과 대응. */
    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Builder
    private CoachingEventOutbox(UUID aggregateId, String eventType, JsonNode payload) {
        this.aggregateId = Validate.requireNonNull(aggregateId, "애그리거트 ID");
        this.eventType = Validate.requireText(eventType, "이벤트 타입");
        this.payload = Validate.requireNonNull(payload, "이벤트 payload").deepCopy();
        this.status = OutboxStatus.PENDING;
        this.attemptCount = 0;
    }

    /** 코칭 세션 완료 트랜잭션 안에서 같은 트랜잭션으로 저장한다. */
    public static CoachingEventOutbox create(UUID aggregateId, String eventType, JsonNode payload) {
        return CoachingEventOutbox.builder()
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();
    }

    public JsonNode getPayload() {
        return payload.deepCopy();
    }

    /** #89에서 Relay Worker가 Kafka 발행 시도(성공/실패 무관)마다 호출 — 재시도 상한 판단용. */
    public void incrementAttemptCount() {
        this.attemptCount = Math.addExact(this.attemptCount, 1);
    }

    /** #89에서 Relay Worker가 Kafka 발행에 성공했을 때 호출. */
    public void markPublished() {
        this.status = OutboxStatus.COMPLETED;
        this.processedAt = Instant.now();
    }
}
