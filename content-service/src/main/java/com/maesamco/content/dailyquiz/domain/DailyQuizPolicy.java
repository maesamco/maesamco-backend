package com.maesamco.content.dailyquiz.domain;

/**
 * Daily Quiz 한 세트의 문항 수 정책입니다.
 *
 * <p>문항 수 정책을 변경할 때는
 * {@code p_daily_quiz_attempts.total_count}의 DB 제약조건도 함께 변경해야 합니다.</p>
 */
public final class DailyQuizPolicy {

    public static final int TARGET_QUESTION_COUNT = 5;
    public static final int MINIMUM_QUESTION_COUNT = 3;

    private DailyQuizPolicy() {
    }
}
