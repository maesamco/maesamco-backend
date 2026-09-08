package com.maesamco.judge.application.result;

import com.maesamco.judge.domain.entity.SubmissionStatus;
import java.util.UUID;

public record SubmissionCreateResult(
        UUID submissionId,
        SubmissionStatus status,
        boolean created
) {
    public static SubmissionCreateResult created(UUID submissionId, SubmissionStatus status) {
        return new SubmissionCreateResult(submissionId, status, true);
    }
    public static SubmissionCreateResult existing(UUID submissionId, SubmissionStatus status) {
        return new SubmissionCreateResult(submissionId, status, false);
    }
}