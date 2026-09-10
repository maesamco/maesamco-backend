package com.maesamco.judge.application.query;

import java.util.UUID;

public record SubmissionGetQuery(UUID submissionId) {

    public static SubmissionGetQuery from(UUID submissionId) {
        return new SubmissionGetQuery(submissionId);
    }
}