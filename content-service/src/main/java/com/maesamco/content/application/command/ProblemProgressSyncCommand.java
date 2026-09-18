package com.maesamco.content.application.command;

import java.time.Instant;
import java.util.UUID;

/**
 * SubmissionJudged Kafka 이벤트를 기반으로
 * ProblemProgress를 생성하거나 갱신하기 위한 Command입니다.
 */
public record ProblemProgressSyncCommand(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        UUID problemVersionId,
        int attemptNo,
        String status,
        String result,
        Instant judgedAt
) {
}