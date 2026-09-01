package com.maesamco.content.dailyquiz.domain;

/**
 * 일일 퀴즈에서 제공하는 문항 유형입니다.
 */
public enum DailyQuizProblemType {

    /** 사용자가 짧은 문자열 답을 직접 입력하며, 허용 답안 표현도 함께 비교할 수 있습니다. */
    SHORT_ANSWER,

    /** 빈칸에 들어갈 문자열을 사용자가 직접 입력하는 문항입니다. */
    FILL_IN_BLANK,

    /** 제공된 선택지 중 하나를 고르는 객관식 문항입니다. */
    MULTIPLE_CHOICE
}
