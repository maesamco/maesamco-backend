package com.maesamco.judge.application.result;

import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.entity.SubmissionTestResult;
import java.util.List;
import java.util.UUID;

public record SubmissionGetResult(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        String code,
        SubmissionStatus status,
        SubmissionResult result,
        FailureCode failureCode,
        List<FailedTestItem> failedTestSummary,
        int attemptNo
) {

    public record FailedTestItem(boolean isPublic, String errorType) {
    }

    public static SubmissionGetResult of(Submission submission, List<SubmissionTestResult> failedResults) {
        List<FailedTestItem> summaries = failedResults.stream()
                .map(r -> new FailedTestItem(
                        r.isPublic(),
                        r.getErrorType() != null ? r.getErrorType().name() : null
                ))
                .toList();

        return new SubmissionGetResult(
                submission.getId(),
                submission.getUserId(),
                submission.getProblemId(),
                submission.getCode(),
                submission.getStatus(),
                submission.getResult(),
                submission.getFailureCode(),
                summaries,
                submission.getAttemptNo()
        );
    }
}