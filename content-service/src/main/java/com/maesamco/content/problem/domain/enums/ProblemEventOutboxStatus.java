package com.maesamco.content.problem.domain.enums;

/**
 * 문제 이벤트 Outbox의 발행 상태입니다.
 */
public enum ProblemEventOutboxStatus {

    /**
     * Kafka 발행 대기 상태입니다.
     */
    PENDING,

    /**
     * Kafka 발행이 완료된 상태입니다.
     */
    PUBLISHED,

    /**
     * 재시도로 복구할 수 없는 발행 실패 상태입니다.
     */
    FAILED
}
