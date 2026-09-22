package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
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
 * 이슈 #51이 이 엔티티를 만들고 저장하는 것(완료 처리와 같은 트랜잭션)을 담당했고,
 * `CoachingEventRelayFacade`(이슈 #89)가 폴링해서 실제로 Kafka에 발행한다.
 *
 * payload는 AiFeedback과 동일한 이유로 JsonNode + `@JdbcTypeCode(SqlTypes.JSON)`을 쓴다
 * (Judge Service는 String + columnDefinition="jsonb"를 쓰지만, coaching-service에선 이미
 * Testcontainers로 왕복 검증까지 마친 JsonNode 패턴이 있어 그걸 그대로 따른다).
 *
 * 발생한 사실을 기록하고, 수정·삭제 대상은 아니다(팀 컨벤션 16절, append-only Outbox
 * 계열) — `markPublished()`만 상태를 바꾼다.
 *
 * PR #123 재검토 2차(용현님, 2026-09-10) 반영 — `version`으로 낙관적 락을 건다.
 * `CoachingEventOutboxPersistenceService`의 각 메서드가 id로 재조회한 뒤 status가
 * PENDING인지 확인하고 저장하는 것만으로는, 두 Relay 인스턴스가 거의 동시에 같은 행을
 * PENDING으로 읽어버리는 진짜 동시 실행까지는 막지 못한다(check-then-act). 낙관적 락을
 * 걸면 나중에 flush되는 트랜잭션이 `ObjectOptimisticLockingFailureException`으로 걸러져,
 * 호출자가 "다른 Relay가 이미 처리함"과 동일하게 무시할 수 있다.
 *
 * 이슈 #261 — 위 낙관적 락은 "발행 결과를 DB에 기록하는" 시점의 쓰기 충돌만 막을 뿐,
 * 그보다 먼저 일어나는 "조회" 시점의 경합은 막지 못한다. 두 Relay 인스턴스가 폴링에서
 * 같은 PENDING 행을 동시에 읽으면 둘 다 Kafka에 발행을 시도해버려서, DB 쓰기는 낙관적
 * 락으로 하나만 성공하더라도 Kafka에는 이미 이벤트가 중복 발행된 뒤다. content-service
 * DailyQuizEventOutbox(PR #244)와 동일한 claim/lease(선점) 패턴을 도입한다 —
 * `claimForPublish()`가 `SELECT ... FOR UPDATE SKIP LOCKED`로 원자적으로 선점하며
 * IN_PROGRESS로 전이시키고, `claimId`(fencing token)와 `leaseUntil`(선점 만료 시각)을
 * 함께 기록해서 lease 만료 후 재선점된 행에 이전 Worker가 뒤늦게 결과를 쓰는 것도 막는다.
 */
@Entity
@Table(
        name = "p_coaching_event_outboxes",
        indexes = @Index(name = "idx_coaching_event_outboxes_status_created_at", columnList = "status, created_at")
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

    // PR #123 재검토 2차 — recordPostPublishFailure()의 무한 재시도가 Relay의 oldest-first
    // LIMIT 100 폴링과 결합되면 head-of-line blocking을 일으킬 수 있어(용현님 리뷰,
    // 2026-09-10), 실패할 때마다 지수 백오프로 다음 재시도 가능 시각을 기록한다.
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** 이슈 #261 — 현재 선점을 식별하는 fencing token. IN_PROGRESS일 때만 값이 있다. */
    @Column(name = "claim_id")
    private UUID claimId;

    /** 이슈 #261 — 이 선점이 만료되는 시각. 지나면 다른 Worker가 재선점할 수 있다. */
    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(30);

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

    /**
     * 이슈 #261 — Relay Worker가 Kafka 발행을 시작하기 위해 이 Outbox를 선점한다.
     * PENDING(재시도 대기 시각이 지난) 행이거나, lease가 만료된 IN_PROGRESS 행만
     * 선점할 수 있다. 호출자(Repository 구현체)는 이 메서드를
     * {@code SELECT ... FOR UPDATE SKIP LOCKED}로 잠근 행에만 호출해야 한다 — 그렇지
     * 않으면 이 메서드의 상태 검증만으로는 진짜 동시 선점을 막지 못한다.
     */
    public void claimForPublish(UUID claimId, Instant claimedAt, Instant leaseUntil) {
        UUID validatedClaimId = Validate.requireNonNull(claimId, "발행 선점 ID");
        if (!leaseUntil.isAfter(claimedAt)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "발행 선점 만료 시각은 선점 시각 이후여야 합니다."
            );
        }

        boolean pendingAndReady = this.status == OutboxStatus.PENDING
                && (this.nextAttemptAt == null || !this.nextAttemptAt.isAfter(claimedAt));
        boolean expiredClaim = this.status == OutboxStatus.IN_PROGRESS
                && this.leaseUntil != null && !this.leaseUntil.isAfter(claimedAt);
        if (!pendingAndReady && !expiredClaim) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "선점 가능한 상태가 아닙니다. status=" + this.status
            );
        }

        this.status = OutboxStatus.IN_PROGRESS;
        this.claimId = validatedClaimId;
        this.leaseUntil = leaseUntil;
        this.nextAttemptAt = null;
    }

    /** Relay Worker가 Kafka 발행 시도(성공/실패 무관)마다 호출 — 재시도 상한 판단용. */
    public void incrementAttemptCount() {
        this.attemptCount = Math.addExact(this.attemptCount, 1);
    }

    /** Relay Worker가 Kafka 발행에 성공했을 때 호출. */
    public void markPublished() {
        this.status = OutboxStatus.COMPLETED;
        this.processedAt = Instant.now();
        clearClaim();
    }

    /** Relay Worker가 재시도 상한까지 발행에 실패했을 때 호출 — 더 이상 재시도하지 않는다. */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.processedAt = Instant.now();
        clearClaim();
    }

    /**
     * 실패한 시도 직후 호출 — 선점을 풀어 PENDING으로 되돌리고, 지수 백오프로 다음
     * 재시도 가능 시각을 계산해 기록한다. {@code attemptCount}가 이미 증가된 뒤(호출자가
     * {@link #incrementAttemptCount()}를 먼저 호출한 뒤)라고 가정한다. 상한(30분) 이후로는
     * 더 늘어나지 않는다.
     */
    public void scheduleNextAttempt() {
        long backoffSeconds = Math.min(
                BASE_RETRY_DELAY.toSeconds() * (1L << Math.min(attemptCount, 20)),
                MAX_RETRY_DELAY.toSeconds()
        );
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds);
        clearClaim();
    }

    private void clearClaim() {
        this.claimId = null;
        this.leaseUntil = null;
    }
}
