package com.maesamco.coaching.domain.entity;

/**
 * AI를 호출한 목적 — Flyway V1 베이스라인의 CHECK(purpose IN ('HINT','FOLLOWUP_QUESTION','FEEDBACK'))
 * 제약과 1:1 대응된다.
 */
public enum AiCallPurpose {

    /** 오답 단계별 힌트 생성을 위한 호출. */
    HINT,

    /** AI 역질문 생성을 위한 호출. */
    FOLLOWUP_QUESTION,

    /** AI 종합 이해도 피드백 생성을 위한 호출. */
    FEEDBACK
}
