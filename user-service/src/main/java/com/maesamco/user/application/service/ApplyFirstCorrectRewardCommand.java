package com.maesamco.user.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 최초 정답 보상을 반영하기 위한 명령입니다.
 */
public record ApplyFirstCorrectRewardCommand(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        Instant judgedAt
) {

    public ApplyFirstCorrectRewardCommand {
        Objects.requireNonNull(submissionId, "제출 ID는 필수입니다.");
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(problemId, "문제 ID는 필수입니다.");
        Objects.requireNonNull(judgedAt, "채점 완료 시각은 필수입니다.");
    }
}
