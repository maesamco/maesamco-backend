package com.maesamco.judge.domain.entity;

/**
 * 채점 결과 (status = COMPLETED 일 때만 값 존재)
 */
public enum SubmissionResult {
    CORRECT,
    WRONG,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED
}