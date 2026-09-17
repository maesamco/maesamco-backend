package com.maesamco.judge.application.command;

public record ExecutionValidationTestCase(
        String input,
        String expectedOutput,
        int cpuTimeLimitSeconds,
        int memoryLimitKb
) {
}
