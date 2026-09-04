package com.maesamco.content.problem.domain.enums;

public enum ProblemType {

    /** 코드 작성 */
    CODE,

    /** 단답형 */
    SHORT_ANSWER,

    /** 서술형 */
    LONG_ANSWER,

    /** 객관식 1개 선택 */
    ONE_CHOICE,

    /** 객관식 2개 이상 선택 */
    MULTIPLE_CHOICE,

    /** 빈칸 채우기 */
    FILL_IN_BLANK
}