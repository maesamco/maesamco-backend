package com.maesamco.judge.presentation.response;

import com.maesamco.judge.application.result.SubmissionGetResult;
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

    public static SubmissionInternalGetResponse from(SubmissionGetResult result) {
        List<FailedTestSummary> summaries = result.failedTestSummary().stream()
                .map(f -> new FailedTestSummary(f.isPublic(), f.errorType()))
                .toList();

        return new SubmissionInternalGetResponse(
                result.submissionId(),
                result.userId(),
                result.problemId(),
                result.code(),
                result.status().name(),
                result.result() != null ? result.result().name() : null,
                result.failureCode() != null ? result.failureCode().name() : null,
                summaries,
                result.attemptNo()
        );
    }
}