package com.maesamco.content.application.facade;

import com.maesamco.content.application.persistence_service.ProblemEventOutboxPersistenceService;
import com.maesamco.content.application.port.EventPublishOutcomeUnknownException;
import com.maesamco.content.application.port.EventPublisherPort;
import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemEventRelayFacadeTest {

    private static final String TOPIC = "problem-published";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_PAYLOAD_BYTES = 100;

    @Mock
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    @Mock
    private ProblemEventOutboxPersistenceService problemEventOutboxPersistenceService;

    @Mock
    private EventPublisherPort eventPublisherPort;

    private ProblemEventRelayFacade problemEventRelayFacade;

    @BeforeEach
    void setUp() {
        problemEventRelayFacade = new ProblemEventRelayFacade(
                problemEventOutboxRepository,
                problemEventOutboxPersistenceService,
                eventPublisherPort
        );

        ReflectionTestUtils.setField(problemEventRelayFacade, "problemPublishedTopic", TOPIC);
        ReflectionTestUtils.setField(problemEventRelayFacade, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(problemEventRelayFacade, "maxPayloadBytes", MAX_PAYLOAD_BYTES);
    }

    @Test
    @DisplayName("PENDING Outbox가 없으면 이벤트를 발행하지 않는다")
    void relay_noPendingOutbox_doesNothing() {
        // given
        when(problemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                ProblemEventOutboxStatus.PENDING,
                BATCH_SIZE
        )).thenReturn(List.of());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxRepository).findAllByStatusOrderByOccurredAtAscIdAsc(
                ProblemEventOutboxStatus.PENDING,
                BATCH_SIZE
        );

        verifyNoInteractions(eventPublisherPort, problemEventOutboxPersistenceService);
    }

    @Test
    @DisplayName("PENDING Outbox 발행에 성공하면 Kafka 발행 후 PUBLISHED 상태로 변경한다")
    void relay_publishSuccess_marksPublished() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                ProblemEventOutboxStatus.PENDING,
                BATCH_SIZE
        )).thenReturn(List.of(outbox));

        // when
        problemEventRelayFacade.relay();

        // then
        InOrder inOrder = inOrder(eventPublisherPort, problemEventOutboxPersistenceService);

        inOrder.verify(eventPublisherPort).publish(
                TOPIC,
                outbox.getAggregateId().toString(),
                outbox.getPayload()
        );

        inOrder.verify(problemEventOutboxPersistenceService)
                .markPublished(outbox.getId());

        verifyNoMoreInteractions(eventPublisherPort, problemEventOutboxPersistenceService);
    }

    @Test
    @DisplayName("payload가 최대 크기를 초과하면 Kafka로 발행하지 않고 FAILED 처리한다")
    void relay_payloadTooLarge_marksFailedWithoutPublishing() {
        // given
        String oversizedPayload = "a".repeat(MAX_PAYLOAD_BYTES + 1);
        ProblemEventOutbox outbox = createOutbox(oversizedPayload);

        when(problemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                ProblemEventOutboxStatus.PENDING,
                BATCH_SIZE
        )).thenReturn(List.of(outbox));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService)
                .markFailed(outbox.getId(), "EVENT_PAYLOAD_TOO_LARGE");

        verifyNoInteractions(eventPublisherPort);

        verify(problemEventOutboxPersistenceService, never())
                .markPublished(any());

        verify(problemEventOutboxPersistenceService, never())
                .recordFailedAttempt(any(), anyString());

        verify(problemEventOutboxPersistenceService, never())
                .recordPostPublishFailure(any(), anyString());
    }

    @Test
    @DisplayName("Kafka 발행 결과를 확정할 수 없으면 PENDING 유지를 위해 post publish 실패를 기록한다")
    void relay_publishOutcomeUnknown_recordsPostPublishFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                ProblemEventOutboxStatus.PENDING,
                BATCH_SIZE
        )).thenReturn(List.of(outbox));

        EventPublishOutcomeUnknownException exception =
                new EventPublishOutcomeUnknownException(
                        "Kafka publish outcome unknown",
                        new RuntimeException("timeout")
                );

        doThrow(exception)
                .when(eventPublisherPort)
                .publish(
                        TOPIC,
                        outbox.getAggregateId().toString(),
                        outbox.getPayload()
                );

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService)
                .recordPostPublishFailure(
                        outbox.getId(),
                        "KAFKA_PUBLISH_OUTCOME_UNKNOWN"
                );

        verify(problemEventOutboxPersistenceService, never())
                .recordFailedAttempt(any(), anyString());

        verify(problemEventOutboxPersistenceService, never())
                .markPublished(any());

        verify(problemEventOutboxPersistenceService, never())
                .markFailed(any(), anyString());
    }

    @Test
    @DisplayName("Kafka 발행 자체가 실패하면 재시도 가능한 실패로 기록한다")
    void relay_publishFailure_recordsFailedAttempt() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                ProblemEventOutboxStatus.PENDING,
                BATCH_SIZE
        )).thenReturn(List.of(outbox));

        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(eventPublisherPort)
                .publish(
                        TOPIC,
                        outbox.getAggregateId().toString(),
                        outbox.getPayload()
                );

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService)
                .recordFailedAttempt(
                        outbox.getId(),
                        "KAFKA_PUBLISH_FAILED:IllegalStateException"
                );

        verify(problemEventOutboxPersistenceService, never())
                .recordPostPublishFailure(any(), anyString());

        verify(problemEventOutboxPersistenceService, never())
                .markPublished(any());

        verify(problemEventOutboxPersistenceService, never())
                .markFailed(any(), anyString());
    }

    @Test
    @DisplayName("Kafka 발행 성공 후 PUBLISHED 저장에 실패하면 post publish 실패를 기록한다")
    void relay_markPublishedFails_recordsPostPublishFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                ProblemEventOutboxStatus.PENDING,
                BATCH_SIZE
        )).thenReturn(List.of(outbox));

        doThrow(new IllegalStateException("database error"))
                .when(problemEventOutboxPersistenceService)
                .markPublished(outbox.getId());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(
                TOPIC,
                outbox.getAggregateId().toString(),
                outbox.getPayload()
        );

        verify(problemEventOutboxPersistenceService)
                .markPublished(outbox.getId());

        verify(problemEventOutboxPersistenceService)
                .recordPostPublishFailure(
                        outbox.getId(),
                        "OUTBOX_POST_PUBLISH_FAILURE"
                );

        verify(problemEventOutboxPersistenceService, never())
                .recordFailedAttempt(any(), anyString());

        verify(problemEventOutboxPersistenceService, never())
                .markFailed(any(), anyString());
    }

    @Test
    @DisplayName("하나의 Outbox 처리 중 예상치 못한 예외가 발생해도 다음 Outbox를 계속 처리한다")
    void relay_oneOutboxFails_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("a".repeat(MAX_PAYLOAD_BYTES + 1));
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        when(problemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                ProblemEventOutboxStatus.PENDING,
                BATCH_SIZE
        )).thenReturn(List.of(first, second));

        doThrow(new IllegalStateException("database error"))
                .when(problemEventOutboxPersistenceService)
                .markFailed(first.getId(), "EVENT_PAYLOAD_TOO_LARGE");

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService)
                .markFailed(first.getId(), "EVENT_PAYLOAD_TOO_LARGE");

        verify(eventPublisherPort).publish(
                TOPIC,
                second.getAggregateId().toString(),
                second.getPayload()
        );

        verify(problemEventOutboxPersistenceService)
                .markPublished(second.getId());
    }

    private ProblemEventOutbox createOutbox(String payload) {
        ProblemEventOutbox outbox = ProblemEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                payload,
                Instant.parse("2026-09-21T00:00:00Z")
        );

        ReflectionTestUtils.setField(outbox, "id", UUID.randomUUID());

        return outbox;
    }
}