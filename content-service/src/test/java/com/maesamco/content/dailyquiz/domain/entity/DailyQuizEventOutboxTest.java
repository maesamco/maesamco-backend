package com.maesamco.content.dailyquiz.domain.entity;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Daily Quiz 완료 이벤트 Outbox의 생성 규칙을 검증합니다.
 */
class DailyQuizEventOutboxTest {

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-16T00:00:00Z");

    @Test
    @DisplayName("DailyQuizCompleted Outbox를 생성하면 PENDING 상태로 초기화된다")
    void createPending_initializesPendingOutbox() {
        UUID eventId = UUID.randomUUID();
        UUID quizAttemptId = UUID.randomUUID();
        String payload = "{\"eventType\":\"DAILY_QUIZ_COMPLETED\"}";

        DailyQuizEventOutbox outbox =
                DailyQuizEventOutbox.createPending(
                        eventId,
                        quizAttemptId,
                        1,
                        payload,
                        OCCURRED_AT
                );

        assertThat(outbox.getEventId()).isEqualTo(eventId);
        assertThat(outbox.getAggregateType()).isEqualTo("DAILY_QUIZ");
        assertThat(outbox.getAggregateId()).isEqualTo(quizAttemptId);
        assertThat(outbox.getEventType()).isEqualTo("DAILY_QUIZ_COMPLETED");
        assertThat(outbox.getEventVersion()).isEqualTo(1);
        assertThat(outbox.getPayload()).isEqualTo(payload);
        assertThat(outbox.getStatus()).isEqualTo(DailyQuizEventOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getVersion()).isZero();
        assertThat(outbox.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    @DisplayName("이벤트 ID가 없으면 Outbox를 생성할 수 없다")
    void createPending_rejectsNullEventId() {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                null,
                UUID.randomUUID(),
                1,
                "{}",
                OCCURRED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이벤트 ID는 필수입니다.");
    }

    @Test
    @DisplayName("퀴즈 세트 ID가 없으면 Outbox를 생성할 수 없다")
    void createPending_rejectsNullQuizAttemptId() {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                null,
                1,
                "{}",
                OCCURRED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("퀴즈 세트 ID는 필수입니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("이벤트 버전이 1 미만이면 Outbox를 생성할 수 없다")
    void createPending_rejectsInvalidEventVersion(int eventVersion) {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventVersion,
                "{}",
                OCCURRED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이벤트 버전은 1 이상이어야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    @DisplayName("이벤트 본문이 비어 있으면 Outbox를 생성할 수 없다")
    void createPending_rejectsBlankPayload(String payload) {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                payload,
                OCCURRED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이벤트 본문은 필수입니다.");
    }

    @Test
    @DisplayName("이벤트 발생 시각이 없으면 Outbox를 생성할 수 없다")
    void createPending_rejectsNullOccurredAt() {
        assertThatThrownBy(() -> DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "{}",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이벤트 발생 시각은 필수입니다.");
    }
}
