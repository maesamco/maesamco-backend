package com.maesamco.judge.application.result;

public record ExecutionValidationResult(
        int testCaseIndex,
        boolean passed,
        boolean timedOut,
        boolean systemError,
        String stdout
) {
}