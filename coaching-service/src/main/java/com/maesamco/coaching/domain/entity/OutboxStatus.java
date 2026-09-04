package com.maesamco.coaching.domain.entity;

/**
 * Outbox 레코드의 발행 상태 — Flyway V8 baseline의 CHECK(status IN ('PENDING','COMPLETED'))
 * 제약과 1:1 대응된다. judge_schema.p_submission_event_outboxes(이슈 #63)와 동일한 설계.
 */
public enum OutboxStatus {

    /** 아직 Relay Worker가 발행하지 않음. */
    PENDING,

    /** Relay Worker가 Kafka 발행에 성공함. */
    COMPLETED
}
