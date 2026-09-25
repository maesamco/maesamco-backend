package com.maesamco.judge.domain.entity;

public enum OutboxStatus {
    PENDING,
    /** Relay Worker가 선점해 발행 중인 상태(#272). lease가 만료되면 다른 Worker가 재선점할 수 있다. */
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
