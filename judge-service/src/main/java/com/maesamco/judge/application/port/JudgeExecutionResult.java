package com.maesamco.judge.application.port;

public record JudgeExecutionResult(
        String token,
        JudgeExecutionStatus status,
        String stdout,
        String stderr,
        String compileOutput,
        Long executionTimeMs,
        Integer memoryUsedKb
) {
}