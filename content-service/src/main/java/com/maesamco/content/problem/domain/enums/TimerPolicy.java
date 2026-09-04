package com.maesamco.content.problem.domain.enums;

public enum TimerPolicy {

    /** 1분 제한 */
    APPLY60,

    /** 2분 제한 */
    APPLY120,

    /** 3분 제한 */
    APPLY180,

    /** 5분 제한 */
    APPLY300,

    /** 타이머 적용 안 함 */
    NOTAPPLY_TimePolicy
}