package com.maesamco.content.dailyquiz.domain;

/**
 * 사용자별 일일 퀴즈 세트의 진행 상태입니다.
 *
 * <p>정상 상태 전이는 READY} → IN_PROGRESS → COMPLETED} 순서입니다.
 */
public enum DailyQuizAttemptStatus {

    /** 세트는 생성됐지만 사용자가 아직 퀴즈 화면에 처음 진입하지 않은 상태입니다. */
    READY,

    /** 사용자가 퀴즈 화면에 진입해 풀이를 시작했으며, 문항을 제출할 수 있는 상태입니다. */
    IN_PROGRESS,

    /** 배정된 모든 문항의 제출과 채점이 끝난 최종 상태입니다. */
    COMPLETED
}
