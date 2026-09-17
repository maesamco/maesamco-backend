package com.maesamco.judge.presentation.response;

import com.maesamco.judge.application.result.SubmissionInternalGetResult;

import java.util.List;
import java.util.UUID;

public record SubmissionInternalGetResponse(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        UUID problemVersionId,
        String code,
        String status,
        String result,
        String failureCode,
        List<FailedTestSummary> failedTestSummary,
        Integer attemptNo
) {

    public record FailedTestSummary(boolean isPublic, String errorType) {
    }

    public static SubmissionInternalGetResponse from(SubmissionInternalGetResult result) {
        List<FailedTestSummary> summaries = result.failedTestSummary().stream()
                .map(f -> new FailedTestSummary(f.isPublic(), f.errorType()))
                .toList();

        return new SubmissionInternalGetResponse(
                result.submissionId(),
                result.userId(),
                result.problemId(),
                result.problemVersionId(),
                result.code(),
                result.status().name(),
                result.result() != null ? result.result().name() : null,
                result.failureCode() != null ? result.failureCode().name() : null,
                summaries,
                result.attemptNo()
        );
    }
}