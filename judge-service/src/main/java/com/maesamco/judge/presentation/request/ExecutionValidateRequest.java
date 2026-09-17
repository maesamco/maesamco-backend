package com.maesamco.judge.presentation.request;


import com.maesamco.judge.application.command.ExecutionValidationTestCase;

import java.util.List;

public record ExecutionValidateRequest(
        String code,
        List<TestCaseItem> testCases
) {
    public record TestCaseItem(
            String input,
            String expectedOutput,
            int cpuTimeLimitSeconds,
            int memoryLimitKb
    ) {
        public ExecutionValidationTestCase toCommand() {
            return new ExecutionValidationTestCase(input, expectedOutput, cpuTimeLimitSeconds, memoryLimitKb);
        }
    }

    public List<ExecutionValidationTestCase> toCommands() {
        return testCases.stream().map(TestCaseItem::toCommand).toList();
    }
}
