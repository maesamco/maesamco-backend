package com.maesamco.user.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ApplyFirstCorrectRewardCommandTest {

    @Test
    @DisplayName("제출 ID가 없으면 최초 정답 보상 명령 생성을 거부한다")
    void rejectsNullSubmissionId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new ApplyFirstCorrectRewardCommand(
                                null,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                Instant.now()
                        )
                )
                .withMessage("제출 ID는 필수입니다.");
    }
}
