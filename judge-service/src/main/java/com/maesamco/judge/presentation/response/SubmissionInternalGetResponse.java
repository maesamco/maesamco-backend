package com.maesamco.judge.presentation.response;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionTestResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import java.util.List;
import java.util.UUID;

public record SubmissionInternalGetResponse(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        String code,
        String status,
        String result,
        String failureCode,
        List<FailedTestSummary> failedTestSummary,
        Integer attemptNo
) {

    public record FailedTestSummary(boolean isPublic, String errorType) {
    }

    public static SubmissionInternalGetResponse of(Submission submission, List<SubmissionTestResult> failedResults) {
        boolean isCompleted = submission.getStatus() == SubmissionStatus.COMPLETED;

        String result = isCompleted && submission.getResult() != null
                ? submission.getResult().name()
                : null;

        List<FailedTestSummary> summaries = failedResults.stream()
                .map(r -> new FailedTestSummary(
                        r.isPublic(),
                        r.getErrorType() != null ? r.getErrorType().name() : null
                ))
                .toList();

        String failureCode = submission.getFailureCode() != null
                ? submission.getFailureCode().name()
                : null;

        return new SubmissionInternalGetResponse(
                submission.getId(),
                submission.getUserId(),
                submission.getProblemId(),
                submission.getCode(),
                submission.getStatus().name(),
                result,
                failureCode,
                summaries,
                submission.getAttemptNo()
        );
    }
}