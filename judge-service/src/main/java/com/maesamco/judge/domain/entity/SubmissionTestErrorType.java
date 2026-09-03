package com.maesamco.judge.domain.entity;

public enum SubmissionTestErrorType {
    WRONG_ANSWER,  // 출력값이 기대값과 다름
    RUNTIME_ERROR,  // 실행 중 예외 발생(NPE, 배열 범위 초과 등등)
    TIME_LIMIT_EXCEEDED,  // 해당 테스트케이스가 시간이 초과됨
    MEMORY_LIMIT_EXCEEDED  // 해당 테스트케이스가 메모리 초과됨
}
