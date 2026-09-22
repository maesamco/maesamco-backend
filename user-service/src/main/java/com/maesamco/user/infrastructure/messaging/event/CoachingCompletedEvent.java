package com.maesamco.user.infrastructure.messaging.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Coaching Service가 발행하는 CoachingCompleted 이벤트의 역직렬화 DTO입니다.
 *
 * <p>현재 생산자 계약에는 별도 eventId가 없으므로 코칭 세션마다 유일한
 * {@code coachingId}를 원천 이벤트 식별자로 사용합니다.</p>
 */
public record CoachingCompletedEvent(
        UUID coachingId,
        UUID userId,
        UUID submissionId,
        UUID problemId,
        Instant completedAt
) {

    public CoachingCompletedEvent {
        Objects.requireNonNull(coachingId, "coachingId는 필수입니다.");
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(submissionId, "submissionId는 필수입니다.");
        Objects.requireNonNull(problemId, "problemId는 필수입니다.");
        Objects.requireNonNull(completedAt, "completedAt은 필수입니다.");
    }
}
