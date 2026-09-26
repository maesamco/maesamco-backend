package com.maesamco.user.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Content Service의 Daily Quiz 완료 이벤트를 보상 처리에 전달하는 명령입니다.
 */
public record ApplyDailyQuizCompletedRewardCommand(
        UUID quizAttemptId,
        UUID userId,
        Instant completedAt
) {

    public ApplyDailyQuizCompletedRewardCommand {
        Objects.requireNonNull(quizAttemptId, "퀴즈 시도 ID는 필수입니다.");
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(completedAt, "퀴즈 완료 시각은 필수입니다.");
    }
}
