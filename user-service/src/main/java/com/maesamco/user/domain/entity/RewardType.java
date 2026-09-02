package com.maesamco.user.domain.entity;

/**
 * XP가 지급되거나 차감된 업무 사유를 나타냅니다.
 */
public enum RewardType {

    /** 문제를 최초로 정답 처리해 지급된 XP입니다. */
    FIRST_CORRECT,

    /** 코칭 과정을 완료해 지급된 XP입니다. */
    COACHING_COMPLETED,

    /** 일일 학습 목표를 완료해 지급된 XP입니다. */
    DAILY_GOAL_COMPLETED,

    /** 일일 퀴즈를 완료해 지급된 XP입니다. */
    DAILY_QUIZ_COMPLETED,

    /** 연속 학습 마일스톤을 달성해 지급된 XP입니다. */
    STREAK_MILESTONE,

    /** 관리자가 XP를 지급하거나 회수한 조정 이력입니다. */
    ADMIN_ADJUSTMENT
}
