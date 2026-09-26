package com.maesamco.user.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ApplyDailyQuizCompletedRewardCommandTest {

    private static final UUID QUIZ_ATTEMPT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant COMPLETED_AT =
            Instant.parse("2026-09-20T15:30:00Z");

    @Test
    @DisplayName("퀴즈 시도 ID가 없으면 명령 생성을 거부한다")
    void rejectsNullQuizAttemptId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new ApplyDailyQuizCompletedRewardCommand(
                                null,
                                USER_ID,
                                COMPLETED_AT
                        )
                )
                .withMessage("퀴즈 시도 ID는 필수입니다.");
    }

    @Test
    @DisplayName("사용자 ID가 없으면 명령 생성을 거부한다")
    void rejectsNullUserId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new ApplyDailyQuizCompletedRewardCommand(
                                QUIZ_ATTEMPT_ID,
                                null,
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
                        () -> new ApplyDailyQuizCompletedRewardCommand(
                                QUIZ_ATTEMPT_ID,
                                USER_ID,
                                null
                        )
                )
                .withMessage("퀴즈 완료 시각은 필수입니다.");
    }
}
