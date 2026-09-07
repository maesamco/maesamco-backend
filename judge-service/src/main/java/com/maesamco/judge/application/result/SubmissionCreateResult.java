package com.maesamco.judge.application.result;

import com.maesamco.judge.domain.entity.SubmissionStatus;
import java.util.UUID;

public record SubmissionCreateResult(
        UUID submissionId,
        SubmissionStatus status
) {

    public static SubmissionCreateResult of(UUID submissionId, SubmissionStatus status) {
        return new SubmissionCreateResult(submissionId, status);
    }
}