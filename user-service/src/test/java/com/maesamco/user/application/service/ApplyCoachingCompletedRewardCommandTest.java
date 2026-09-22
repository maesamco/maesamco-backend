package com.maesamco.user.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ApplyCoachingCompletedRewardCommandTest {

    private static final UUID COACHING_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SUBMISSION_ID = UUID.randomUUID();
    private static final UUID PROBLEM_ID = UUID.randomUUID();
    private static final Instant COMPLETED_AT =
            Instant.parse("2026-09-20T15:30:00Z");

    @Test
    @DisplayName("코칭 ID가 없으면 명령 생성을 거부한다")
    void rejectsNullCoachingId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new ApplyCoachingCompletedRewardCommand(
                                null,
                                USER_ID,
                                SUBMISSION_ID,
                                PROBLEM_ID,
                                COMPLETED_AT
                        )
                )
                .withMessage("코칭 ID는 필수입니다.");
    }

    @Test
    @DisplayName("사용자 ID가 없으면 명령 생성을 거부한다")
    void rejectsNullUserId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new ApplyCoachingCompletedRewardCommand(
                                COACHING_ID,
                                null,
                                SUBMISSION_ID,
                                PROBLEM_ID,
                                COMPLETED_AT
                        )
                )
                .withMessage("사용자 ID는 필수입니다.");
    }

    @Test
    @DisplayName("완료 시각이 없으면 명령 생성을 거부한다")
    void rejectsNullCompletedAt() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new ApplyCoachingCompletedRewardCommand(
                                COACHING_ID,
                                USER_ID,
                                SUBMISSION_ID,
                                PROBLEM_ID,
                                null
                        )
                )
                .withMessage("코칭 완료 시각은 필수입니다.");
    }
}
