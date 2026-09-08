package com.maesamco.coaching.domain.entity;

/**
 * Outbox 레코드의 발행 상태 — Flyway V9의 CHECK(status IN ('PENDING','COMPLETED','FAILED'))
 * 제약과 1:1 대응된다(V8 baseline은 PENDING/COMPLETED만 허용했다). judge_schema.
 * p_submission_event_outboxes(이슈 #63)와 동일한 설계.
 */
public enum OutboxStatus {

    /** 아직 Relay Worker가 발행하지 않음. */
    PENDING,

    /** Relay Worker가 Kafka 발행에 성공함. */
    COMPLETED,

    /** Relay Worker가 재시도 상한까지 발행에 실패해 더 이상 재시도하지 않음. */
    FAILED
}
