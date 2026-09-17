package com.maesamco.content.infrastructure.dailyquiz.messaging;

import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventData;
import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPort;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.infrastructure.dailyquiz.messaging.event.DailyQuizCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Daily Quiz 완료 의미 데이터를 Kafka 이벤트 payload로 변환해 Outbox에 저장하는 Adapter
 */
@Component
@RequiredArgsConstructor
public class DailyQuizCompletedOutboxAdapter implements DailyQuizCompletedEventPort {

    private final DailyQuizEventOutboxRepository eventOutboxRepository;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(DailyQuizCompletedEventData eventData) {
        DailyQuizCompletedEvent event =
                DailyQuizCompletedEvent.from(
                        UUID.randomUUID(),
                        eventData
                );

        String payload = serializeEvent(event);

        DailyQuizEventOutbox outbox =
                DailyQuizEventOutbox.createPending(
                        event.eventId(),
                        event.quizAttemptId(),
                        event.eventType(),
                        event.eventVersion(),
                        payload,
                        event.occurredAt()
                );

        eventOutboxRepository.save(outbox);
    }

    private String serializeEvent(DailyQuizCompletedEvent event) {
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "DailyQuizCompleted 이벤트 직렬화에 실패했습니다."
            );
        }
    }
}
