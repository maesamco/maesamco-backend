package com.maesamco.content.infrastructure.dailyquiz.messaging;

import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventData;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.infrastructure.dailyquiz.messaging.event.DailyQuizCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyQuizCompletedOutboxAdapterTest {

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-16T03:00:00Z");
    private static final Instant COMPLETED_AT =
            Instant.parse("2026-09-16T02:59:59Z");

    @Mock
    private DailyQuizEventOutboxRepository eventOutboxRepository;

    @Mock
    private JsonMapper jsonMapper;

    private DailyQuizCompletedOutboxAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DailyQuizCompletedOutboxAdapter(
                eventOutboxRepository,
                jsonMapper
        );
    }

    @Test
    @DisplayName("완료 의미 데이터를 이벤트 payload로 변환해 동일한 메타데이터의 Outbox를 저장한다")
    void publish_mapsEventDataAndSavesOutbox() throws Exception {
        DailyQuizCompletedEventData eventData = eventData();
        String payload = "{\"eventType\":\"DAILY_QUIZ_COMPLETED\"}";

        when(jsonMapper.writeValueAsString(any(DailyQuizCompletedEvent.class)))
                .thenReturn(payload);

        adapter.publish(eventData);

        ArgumentCaptor<DailyQuizCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(DailyQuizCompletedEvent.class);
        verify(jsonMapper).writeValueAsString(eventCaptor.capture());

        ArgumentCaptor<DailyQuizEventOutbox> outboxCaptor =
                ArgumentCaptor.forClass(DailyQuizEventOutbox.class);
        verify(eventOutboxRepository).save(outboxCaptor.capture());

        DailyQuizCompletedEvent event = eventCaptor.getValue();
        DailyQuizEventOutbox outbox = outboxCaptor.getValue();

        assertThat(event.eventType()).isEqualTo(DailyQuizCompletedEvent.EVENT_TYPE);
        assertThat(event.eventVersion()).isEqualTo(DailyQuizCompletedEvent.EVENT_VERSION);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.quizAttemptId()).isEqualTo(eventData.quizAttemptId());
        assertThat(event.userId()).isEqualTo(eventData.userId());
        assertThat(event.conceptTags()).containsExactly("반복문", "조건문");
        assertThat(event.correctCount()).isEqualTo(1);
        assertThat(event.totalCount()).isEqualTo(2);
        assertThat(event.completedAt()).isEqualTo(COMPLETED_AT);
        assertThat(event.questionResults()).hasSize(2);

        assertThat(outbox.getEventId()).isEqualTo(event.eventId());
        assertThat(outbox.getAggregateId()).isEqualTo(event.quizAttemptId());
        assertThat(outbox.getEventType()).isEqualTo(event.eventType());
        assertThat(outbox.getEventVersion()).isEqualTo(event.eventVersion());
        assertThat(outbox.getPayload()).isEqualTo(payload);
        assertThat(outbox.getOccurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    @DisplayName("이벤트 직렬화에 실패하면 Outbox를 저장하지 않는다")
    void publish_doesNotSaveOutboxWhenSerializationFails() throws Exception {
        when(jsonMapper.writeValueAsString(any(DailyQuizCompletedEvent.class)))
                .thenThrow(new RuntimeException("직렬화 실패"));

        assertThatThrownBy(() -> adapter.publish(eventData()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
                )
                .hasMessage("DailyQuizCompleted 이벤트 직렬화에 실패했습니다.");

        verifyNoInteractions(eventOutboxRepository);
    }

    private DailyQuizCompletedEventData eventData() {
        return new DailyQuizCompletedEventData(
                OCCURRED_AT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                2,
                COMPLETED_AT,
                List.of(
                        new DailyQuizCompletedEventData.QuestionResult(
                                UUID.randomUUID(),
                                List.of("반복문"),
                                true
                        ),
                        new DailyQuizCompletedEventData.QuestionResult(
                                UUID.randomUUID(),
                                List.of("조건문", "반복문"),
                                false
                        )
                )
        );
    }
}
