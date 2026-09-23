package com.maesamco.user.infrastructure.messaging.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionJudgedEventTest {

    private static final Instant JUDGED_AT =
            Instant.parse("2026-09-21T04:30:00Z");

    @Test
    @DisplayName("완료된 정답 결과만 최초 정답 보상 대상이다")
    void isCorrect_requiresCompletedCorrectResult() {
        assertThat(event("COMPLETED", "CORRECT").isCorrect()).isTrue();
        assertThat(event("COMPLETED", "WRONG").isCorrect()).isFalse();
        assertThat(event("FAILED", "CORRECT").isCorrect()).isFalse();
    }

    private SubmissionJudgedEvent event(String status, String result) {
        return new SubmissionJudgedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                result,
                JUDGED_AT
        );
    }
}
