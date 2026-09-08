package com.maesamco.judge.presentation.response;

import com.maesamco.judge.domain.entity.SubmissionStatus;
import java.util.UUID;

public record SubmissionCreateResponse(
        UUID submissionId,
        SubmissionStatus status
) {

    public static SubmissionCreateResponse of(UUID submissionId, SubmissionStatus status) {
        return new SubmissionCreateResponse(submissionId, status);
    }
}