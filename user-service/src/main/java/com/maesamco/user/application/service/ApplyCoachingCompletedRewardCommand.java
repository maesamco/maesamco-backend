package com.maesamco.user.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Coaching Service의 코칭 완료 이벤트를 보상 처리에 전달하는 명령입니다.
 */
public record ApplyCoachingCompletedRewardCommand(
        UUID coachingId,
        UUID userId,
        UUID submissionId,
        UUID problemId,
        Instant completedAt
) {

    public ApplyCoachingCompletedRewardCommand {
        Objects.requireNonNull(coachingId, "코칭 ID는 필수입니다.");
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(submissionId, "제출 ID는 필수입니다.");
        Objects.requireNonNull(problemId, "문제 ID는 필수입니다.");
        Objects.requireNonNull(completedAt, "코칭 완료 시각은 필수입니다.");
    }
}
