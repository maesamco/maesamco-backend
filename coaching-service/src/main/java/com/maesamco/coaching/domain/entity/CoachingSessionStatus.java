package com.maesamco.coaching.domain.entity;

/**
 * 코칭 세션의 진행 상태 — Flyway V1 베이스라인의 CHECK(status IN ('IN_PROGRESS','COMPLETED'))
 * 제약과 1:1 대응된다.
 */
public enum CoachingSessionStatus {

    /** 생성 시 기본 상태 — 힌트/설명/역질문 진행 중. */
    IN_PROGRESS,

    /** 역질문 답변까지 완료된 상태. */
    COMPLETED
}
