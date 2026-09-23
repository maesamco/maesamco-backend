package com.maesamco.coaching.domain.entity;

/**
 * Outbox 레코드의 발행 상태 — Flyway V16__coaching_event_outbox_claim.sql의
 * CHECK(status IN ('PENDING','IN_PROGRESS','COMPLETED','FAILED')) 제약과 1:1 대응된다
 * (V8 baseline은 PENDING/COMPLETED만, V10에서 FAILED가 추가됐다. PR #124가 V9를 먼저
 * 가져가면서 FAILED 추가 마이그레이션이 V9에서 V10으로 밀렸다). 이슈 #261 — 다중 인스턴스가
 * 같은 행을 동시에 선점하지 못하도록 IN_PROGRESS(선점 중)를 추가했다. content-service
 * DailyQuizEventOutbox(PR #244), judge_schema.p_submission_event_outboxes(이슈 #63)와
 * 동일한 설계.
 */
public enum OutboxStatus {

    /** 아직 Relay Worker가 선점하지 않았거나, 재시도 대기 중. */
    PENDING,

    /** Relay Worker가 선점해서 Kafka 발행을 시도하는 중. */
    IN_PROGRESS,

    /** Relay Worker가 Kafka 발행에 성공함. */
    COMPLETED,

    /** Relay Worker가 재시도 상한까지 발행에 실패해 더 이상 재시도하지 않음. */
    FAILED
}
