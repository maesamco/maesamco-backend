package com.maesamco.judge.application.command;

public record ExecutionValidationTestCase(
        String input,
        String expectedOutput,
        double cpuTimeLimitSeconds,
        int memoryLimitKb
) {
}
