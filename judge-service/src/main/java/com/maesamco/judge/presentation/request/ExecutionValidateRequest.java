package com.maesamco.judge.presentation.request;


import com.maesamco.judge.application.command.ExecutionValidationTestCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExecutionValidateRequest(
        @NotBlank(message = "code는 비어 있을 수 없습니다.")
        String code,

        @NotNull(message = "testCases는 필수입니다.")
        @Size(max = 50, message = "testCases는 최대 50개까지 가능합니다.")
        @Valid
        List<TestCaseItem> testCases
) {
    public record TestCaseItem(
            @NotNull(message = "input은 필수입니다.")
            String input,

            @NotNull(message = "expectedOutput은 필수입니다.")
            String expectedOutput,

            @Positive(message = "cpuTimeLimitSeconds는 양수여야 합니다.")
            double cpuTimeLimitSeconds,

            @Positive(message = "memoryLimitKb는 양수여야 합니다.")
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
