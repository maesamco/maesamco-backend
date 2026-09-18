package com.maesamco.content.domain.dailyquiz.entity;

/**
 * Daily Quiz 이벤트 Outbox의 발행 상태
 */
public enum DailyQuizEventOutboxStatus {

    /**
     * Kafka 발행 대기 상태입니다.
     */
    PENDING,

    /**
     * Relay Worker가 Kafka 발행을 위해 선점한 상태입니다.
     */
    IN_PROGRESS,

    /**
     * Kafka 발행이 완료된 상태입니다.
     */
    PUBLISHED,

    /**
     * 최대 재시도 횟수 도달 등으로 발행을 중단한 상태입니다.
     */
    FAILED
}
