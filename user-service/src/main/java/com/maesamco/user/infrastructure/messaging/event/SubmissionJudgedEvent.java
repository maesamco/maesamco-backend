package com.maesamco.user.infrastructure.messaging.event;

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
        String result
) {

    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String CORRECT_RESULT = "CORRECT";

    public SubmissionJudgedEvent {
        Objects.requireNonNull(submissionId, "submissionId는 필수입니다.");
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(problemId, "problemId는 필수입니다.");
        Objects.requireNonNull(status, "status는 필수입니다.");
        Objects.requireNonNull(result, "result는 필수입니다.");
    }

    public boolean isCorrect() {
        return COMPLETED_STATUS.equals(status)
                && CORRECT_RESULT.equals(result);
    }
}
