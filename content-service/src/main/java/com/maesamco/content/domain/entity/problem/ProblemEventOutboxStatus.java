package com.maesamco.content.domain.entity.problem;

/**
 * 문제 이벤트 Outbox의 발행 상태입니다.
 */
public enum ProblemEventOutboxStatus {

    /** Kafka 발행 대기 상태입니다. */
    PENDING,

    /** Relay Worker가 선점하여 Kafka 발행 중인 상태입니다. lease 만료 시 재선점됩니다. */
    IN_PROGRESS,

    /** Kafka 발행이 완료된 상태입니다. */
    PUBLISHED,

    /** 재시도로 복구할 수 없는 발행 실패 상태입니다. */
    FAILED
}
