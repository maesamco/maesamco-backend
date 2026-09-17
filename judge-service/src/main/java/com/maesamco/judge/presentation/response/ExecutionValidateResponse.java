package com.maesamco.judge.presentation.response;

import com.maesamco.judge.application.result.ExecutionValidationResult;
import java.util.List;

public record ExecutionValidateResponse(
        List<ResultItem> results
) {
    public record ResultItem(
            int testCaseIndex,
            boolean passed,
            boolean timedOut,
            String stdout
    ) {}

    public static ExecutionValidateResponse from(List<ExecutionValidationResult> results) {
        return new ExecutionValidateResponse(
                results.stream()
                        .map(r -> new ResultItem(r.testCaseIndex(), r.passed(), r.timedOut(), r.stdout()))
                        .toList()
        );
    }
}