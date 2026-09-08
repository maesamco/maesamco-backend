package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.persistence_service.CoachingEventOutboxPersistenceService;
import com.maesamco.coaching.application.port.EventPublisherPort;
import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoachingEventRelayFacadeTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private CoachingEventOutboxRepository coachingEventOutboxRepository;

    @Mock
    private CoachingEventOutboxPersistenceService coachingEventOutboxPersistenceService;

    @Mock
    private EventPublisherPort eventPublisherPort;

    @InjectMocks
    private CoachingEventRelayFacade coachingEventRelayFacade;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(coachingEventRelayFacade, "coachingCompletedTopic", "coaching-completed");
    }

    private CoachingEventOutbox pendingOutbox() {
        ObjectNode payload = JSON_MAPPER.createObjectNode().put("coachingId", UUID.randomUUID().toString());
        return CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload);
    }

    @Nested
    @DisplayName("relay")
    class Relay {

        @Test
        @DisplayName("PENDING Outbox 발행에 성공하면 markPublished를 호출한다")
        void marksPublishedOnSuccess() {
            CoachingEventOutbox outbox = pendingOutbox();
            given(coachingEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                    .willReturn(List.of(outbox));

            coachingEventRelayFacade.relay();

            verify(eventPublisherPort).publish(
                    "coaching-completed", outbox.getAggregateId().toString(), JSON_MAPPER.writeValueAsString(outbox.getPayload()));
            verify(coachingEventOutboxPersistenceService).markPublished(outbox);
            verify(coachingEventOutboxPersistenceService, never()).recordFailedAttempt(any());
        }

        @Test
        @DisplayName("발행이 실패하면 recordFailedAttempt를 호출하고 예외를 전파하지 않는다")
        void recordsFailedAttemptOnPublishFailure() {
            CoachingEventOutbox outbox = pendingOutbox();
            given(coachingEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                    .willReturn(List.of(outbox));
            willThrow(new IllegalStateException("Kafka 발행 실패"))
                    .given(eventPublisherPort).publish(anyString(), anyString(), anyString());

            coachingEventRelayFacade.relay();

            verify(coachingEventOutboxPersistenceService).recordFailedAttempt(outbox);
            verify(coachingEventOutboxPersistenceService, never()).markPublished(any());
        }

        @Test
        @DisplayName("발행은 성공했지만 markPublished 후처리가 실패하면 recordPostPublishFailure로 넘긴다")
        void recordsPostPublishFailureWhenMarkPublishedFails() {
            CoachingEventOutbox outbox = pendingOutbox();
            given(coachingEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                    .willReturn(List.of(outbox));
            willThrow(new RuntimeException("DB 후처리 실패"))
                    .given(coachingEventOutboxPersistenceService).markPublished(outbox);

            coachingEventRelayFacade.relay();

            verify(eventPublisherPort).publish(anyString(), anyString(), anyString());
            verify(coachingEventOutboxPersistenceService).markPublished(outbox);
            verify(coachingEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId());
            verify(coachingEventOutboxPersistenceService, never()).recordFailedAttempt(any());
        }
    }
}
