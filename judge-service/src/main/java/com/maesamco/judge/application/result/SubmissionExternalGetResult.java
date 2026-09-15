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
        SubmissionStatus status = submission.getStatus();

        List<TestResultItem> items = submission.getStatus() == SubmissionStatus.COMPLETED
                ? testResults.stream()
                .map(t -> new TestResultItem(
                        t.getTestCaseId(), t.isPublic(), t.isPassed(), t.isPublic() ? t.getActualOutput() : null))
                .toList()
                : List.of();

        SubmissionResult result = status == SubmissionStatus.COMPLETED ? submission.getResult() : null;
        FailureCode failureCode = status == SubmissionStatus.FAILED ? submission.getFailureCode() : null;

        return new SubmissionExternalGetResult(
                submission.getId(), submission.getProblemId(), submission.getProblemVersionId(),
                submission.getAttemptNo(), status, result,
                failureCode, items,
                submission.getExecutionTimeMs(), submission.getMemoryUsedKb(),
                submission.getSubmittedAt(), submission.getJudgedAt());
    }
}