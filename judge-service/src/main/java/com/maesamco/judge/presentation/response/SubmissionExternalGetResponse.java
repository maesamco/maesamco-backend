package com.maesamco.judge.presentation.response;

import com.maesamco.judge.application.result.SubmissionExternalGetResult;
import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubmissionExternalGetResponse(
        UUID submissionId,
        UUID problemId,
        UUID problemVersionId,
        int attemptNo,
        SubmissionStatus status,
        SubmissionResult result,
        FailureCode failureCode,
        List<TestResultItem> testResults,
        Integer executionTimeMs,
        Integer memoryUsedKb,
        Instant submittedAt,
        Instant judgedAt
) {

    public record TestResultItem(UUID testCaseId, boolean isPublic, boolean passed, String actualOutput) {
    }

    public static SubmissionExternalGetResponse from(SubmissionExternalGetResult result) {
        List<TestResultItem> items = result.testResults().stream()
                .map(t -> new TestResultItem(t.testCaseId(), t.isPublic(), t.passed(), t.actualOutput()))
                .toList();

        return new SubmissionExternalGetResponse(
                result.submissionId(), result.problemId(), result.problemVersionId(),
                result.attemptNo(), result.status(), result.result(), result.failureCode(),
                items, result.executionTimeMs(), result.memoryUsedKb(),
                result.submittedAt(), result.judgedAt());
    }
}