package com.maesamco.judge.application.result;

import com.maesamco.judge.domain.entity.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubmissionExternalGetResult(
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

    public static SubmissionExternalGetResult of(Submission submission, List<SubmissionTestResult> testResults) {
        List<TestResultItem> items = submission.getStatus() == SubmissionStatus.COMPLETED
                ? testResults.stream()
                .map(t -> new TestResultItem(
                        t.getTestCaseId(), t.isPublic(), t.isPassed(), t.getActualOutput()))
                .toList()
                : List.of();

        return new SubmissionExternalGetResult(
                submission.getId(), submission.getProblemId(), submission.getProblemVersionId(),
                submission.getAttemptNo(), submission.getStatus(), submission.getResult(),
                submission.getFailureCode(), items,
                submission.getExecutionTimeMs(), submission.getMemoryUsedKb(),
                submission.getSubmittedAt(), submission.getJudgedAt());
    }
}