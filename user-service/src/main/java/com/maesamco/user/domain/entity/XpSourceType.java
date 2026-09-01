package com.maesamco.user.domain.entity;

/**
 * XP 지급·차감을 발생시킨 원천 리소스의 종류를 나타냅니다.
 */
public enum XpSourceType {

    /** Judge Service의 문제 제출 결과입니다. */
    SUBMISSION,

    /** Coaching Service의 코칭 완료 결과입니다. */
    COACHING,

    /** 사용자의 일일 학습 활동 결과입니다. */
    DAILY_ACTIVITY,

    /** 일일 퀴즈 완료 결과입니다. */
    DAILY_QUIZ,

    /** 연속 학습 달성 결과입니다. */
    STREAK,

    /** 관리자 조정 등 시스템 내부 처리 결과입니다. */
    SYSTEM
}
