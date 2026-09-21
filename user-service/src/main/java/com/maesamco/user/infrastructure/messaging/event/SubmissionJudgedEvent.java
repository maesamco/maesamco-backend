package com.maesamco.user.infrastructure.messaging.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Judge Service가 발행하는 SubmissionJudged 이벤트의 역직렬화 DTO입니다.
 */
public record SubmissionJudgedEvent(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        String status,
        String result,
        Instant judgedAt
) {

    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String CORRECT_RESULT = "CORRECT";

    public SubmissionJudgedEvent {
        Objects.requireNonNull(submissionId, "submissionId는 필수입니다.");
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(problemId, "problemId는 필수입니다.");
        Objects.requireNonNull(status, "status는 필수입니다.");
        Objects.requireNonNull(result, "result는 필수입니다.");
        // judgedAt 추가 이전에 발행된 Kafka 메시지와의 하위 호환을 위해 null을 허용합니다.
    }

    public boolean isCorrect() {
        return COMPLETED_STATUS.equals(status)
                && CORRECT_RESULT.equals(result);
    }
}
