package com.maesamco.content.infrastructure.adapter.event;

import com.maesamco.content.application.port.ProblemPublishedEventData;
import com.maesamco.content.application.port.ProblemPublishedEventPort;
import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import com.maesamco.content.infrastructure.messaging.event.ProblemPublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * ProblemPublished 이벤트를 생성하고 직렬화하여 Outbox에 기록하는 Adapter입니다.
 */
@Component
@RequiredArgsConstructor
public class ProblemPublishedEventAdapter implements ProblemPublishedEventPort {

    private final ProblemEventOutboxRepository problemEventOutboxRepository;
    private final JsonMapper jsonMapper;

    @Override
    public void record(ProblemPublishedEventData eventData) {
        ProblemPublishedEvent event = ProblemPublishedEvent.fromPublishedVersion(
                eventData.eventId(),
                eventData.occurredAt(),
                eventData.problemVersionId(),
                eventData.problemVersion()
        );

        String payload = serializeEvent(event);

        ProblemEventOutbox outbox = ProblemEventOutbox.createPending(
                event.eventId(),
                event.problemId(),
                event.eventVersion(),
                payload,
                event.occurredAt()
        );

        problemEventOutboxRepository.save(outbox);
    }

    private String serializeEvent(ProblemPublishedEvent event) {
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "ProblemPublished event serialization failed",
                    exception
            );
        }
    }
}