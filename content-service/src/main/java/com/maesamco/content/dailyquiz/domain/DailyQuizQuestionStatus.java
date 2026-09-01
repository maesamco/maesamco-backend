package com.maesamco.content.dailyquiz.domain;

/**
 * 일일 퀴즈 문제 버전의 사용 상태입니다.
 */
public enum DailyQuizQuestionStatus {

    /** 정상 상태로, 새로운 일일 퀴즈 세트를 만들 때 문제은행에서 선택할 수 있습니다. */
    ACTIVE,

    /** 사용자 신고가 접수되어 신규 세트 배정에서 제외하고 관리자 수정을 기다리는 상태입니다. */
    FLAGGED,

    /** 더 이상 신규 세트에 사용하지 않는 이전 버전입니다. 과거 풀이 기록 보존을 위해 행은 유지합니다. */
    DISABLED
}
