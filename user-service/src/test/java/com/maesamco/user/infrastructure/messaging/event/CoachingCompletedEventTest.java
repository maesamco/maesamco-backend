package com.maesamco.user.infrastructure.messaging.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CoachingCompletedEventTest {

    @Test
    @DisplayName("필수 식별자가 없는 CoachingCompleted 이벤트를 거부한다")
    void rejectsMissingRequiredIdentifier() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new CoachingCompletedEvent(
                                null,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                Instant.now()
                        )
                )
                .withMessage("coachingId는 필수입니다.");
    }
}
