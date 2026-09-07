package com.maesamco.judge.application.command;

import com.maesamco.judge.presentation.request.SubmissionCreateRequest;
import java.util.UUID;

public record SubmissionCreateCommand(
        UUID userId,
        String idempotencyKey,
        UUID problemId,
        String code,
        String language
) {

    private static final String DEFAULT_LANGUAGE = "JAVA17";

    public static SubmissionCreateCommand from(UUID userId, String idempotencyKey, SubmissionCreateRequest request) {
        String language = request.language() != null ? request.language() : DEFAULT_LANGUAGE;
        return new SubmissionCreateCommand(userId, idempotencyKey, request.problemId(), request.code(), language);
    }
}