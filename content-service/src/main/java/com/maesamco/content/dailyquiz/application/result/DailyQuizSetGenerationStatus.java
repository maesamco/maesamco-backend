package com.maesamco.content.dailyquiz.application.result;

public enum DailyQuizSetGenerationStatus {
    // 세트 생성 완료
    CREATED,
    // 해당 사용자의 오늘 퀴즈가 이미 존재
    ALREADY_EXISTS,
    // 슬롯을 구성할 수 있는 개념이 존재하지 않음
    NO_AVAILABLE_CONCEPTS,
    // 최소 문항 미달
    INSUFFICIENT_QUESTIONS
}
