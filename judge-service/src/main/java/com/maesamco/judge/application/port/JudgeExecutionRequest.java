package com.maesamco.judge.application.port;

public record JudgeExecutionRequest(
        String sourceCode,
        String stdin,
        String expectedOutput,
        double cpuTimeLimitSeconds,
        int memoryLimitKb
) {
}