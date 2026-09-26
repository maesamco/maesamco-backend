package com.maesamco.user.infrastructure.messaging.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DailyQuizCompletedEventTest {

    /** content-service DailyQuizCompletedEvent(eventVersion 1)가 실제로 발행하는 payload 형태 */
    private static final String CONTENT_SERVICE_PAYLOAD = """
            {
              "eventId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "eventType": "DAILY_QUIZ_COMPLETED",
              "eventVersion": 1,
              "occurredAt": "2026-09-20T15:30:01Z",
              "quizAttemptId": "11111111-1111-1111-1111-111111111111",
              "userId": "22222222-2222-2222-2222-222222222222",
              "conceptTags": ["변수", "반복문"],
              "correctCount": 3,
              "totalCount": 5,
              "completedAt": "2026-09-20T15:30:00Z",
              "questionResults": [
                {
                  "questionVersionId": "33333333-3333-3333-3333-333333333333",
                  "conceptTags": ["변수"],
                  "correct": true
                }
              ]
            }
            """;

    @Test
    @DisplayName("content-service의 전체 payload를 알 수 없는 필드를 거부하는 매퍼로도 역직렬화한다")
    void deserializesContentServicePayloadIgnoringUnknownFields() {
        // given — 매퍼 설정과 관계없이 DTO의 @JsonIgnoreProperties로 알 수 없는 필드를 무시해야 한다
        JsonMapper strictMapper = JsonMapper.builder()
                .findAndAddModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        // when
        DailyQuizCompletedEvent event =
                strictMapper.readValue(CONTENT_SERVICE_PAYLOAD, DailyQuizCompletedEvent.class);

        // then
        assertThat(event.eventId())
                .isEqualTo(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertThat(event.quizAttemptId())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(event.userId())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        assertThat(event.correctCount()).isEqualTo(3);
        assertThat(event.totalCount()).isEqualTo(5);
        assertThat(event.completedAt()).isEqualTo(Instant.parse("2026-09-20T15:30:00Z"));
    }

    @Test
    @DisplayName("퀴즈 시도 ID가 없는 DailyQuizCompleted 이벤트를 거부한다")
    void rejectsMissingQuizAttemptId() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new DailyQuizCompletedEvent(
                                UUID.randomUUID(),
                                null,
                                UUID.randomUUID(),
                                3,
                                5,
                                Instant.now()
                        )
                )
                .withMessage("quizAttemptId는 필수입니다.");
    }

    @Test
    @DisplayName("완료 시각이 없는 DailyQuizCompleted 이벤트를 거부한다")
    void rejectsMissingCompletedAt() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new DailyQuizCompletedEvent(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                3,
                                5,
                                null
                        )
                )
                .withMessage("completedAt은 필수입니다.");
    }
}
