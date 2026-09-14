package com.maesamco.content.problem.domain.enums;

/** 문제 시간 제한 */
public enum RunningTimeLimit {

    /** 1초 */
    SECOND_1(1),

    /** 2초 */
    SECOND_2(2),

    /** 3초 */
    SECOND_3(3),

    /** 5초 */
    SECOND_5(5);

    private final int seconds;

    RunningTimeLimit(int seconds) { this.seconds = seconds; }

    public int getSeconds() { return seconds; }
}