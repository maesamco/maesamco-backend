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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        problemEventRelayFacade = new ProblemEventRelayFacade(problemEventOutboxRepository, problemEventOutboxPersistenceService, eventPublisherPort);

        ReflectionTestUtils.setField(problemEventRelayFacade, "problemPublishedTopic", TOPIC);
        ReflectionTestUtils.setField(problemEventRelayFacade, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(problemEventRelayFacade, "maxPayloadBytes", MAX_PAYLOAD_BYTES);
    }

    @Test
    @DisplayName("발행 가능한 PENDING Outbox가 없으면 이벤트를 발행하지 않는다")
    void relay_noPollablePendingOutbox_doesNothing() {
        // given
        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxRepository).findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE);
        verifyNoInteractions(eventPublisherPort, problemEventOutboxPersistenceService);
    }

    @Test
    @DisplayName("Relay는 PENDING 상태의 발행 가능한 Outbox를 설정된 batch size만큼 조회한다")
    void relay_queriesPollablePendingOutboxesWithBatchSize() {
        // given
        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxRepository, times(1)).findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE);
    }

    @Test
    @DisplayName("발행 가능한 PENDING Outbox 발행에 성공하면 Kafka 발행 후 PUBLISHED 상태로 변경한다")
    void relay_publishSuccess_marksPublished() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));

        // when
        problemEventRelayFacade.relay();

        // then
        InOrder inOrder = inOrder(eventPublisherPort, problemEventOutboxPersistenceService);

        inOrder.verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        inOrder.verify(problemEventOutboxPersistenceService).markPublished(outbox.getId());

        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).recordPostPublishFailure(any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("payload 크기가 최대 허용 크기와 정확히 같으면 Kafka로 발행한다")
    void relay_payloadExactlyMaxSize_publishesSuccessfully() {
        // given
        String payload = "a".repeat(MAX_PAYLOAD_BYTES);
        ProblemEventOutbox outbox = createOutbox(payload);

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), payload);
        verify(problemEventOutboxPersistenceService).markPublished(outbox.getId());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("payload가 최대 허용 크기를 초과하면 Kafka로 발행하지 않고 FAILED 처리한다")
    void relay_payloadTooLarge_marksFailedWithoutPublishing() {
        // given
        String oversizedPayload = "a".repeat(MAX_PAYLOAD_BYTES + 1);
        ProblemEventOutbox outbox = createOutbox(oversizedPayload);

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).markFailed(outbox.getId(), "EVENT_PAYLOAD_TOO_LARGE");
        verifyNoInteractions(eventPublisherPort);
        verify(problemEventOutboxPersistenceService, never()).markPublished(any());
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).recordPostPublishFailure(any(), anyString());
    }

    @Test
    @DisplayName("Kafka 발행 결과를 확정할 수 없으면 post publish 실패를 기록한다")
    void relay_publishOutcomeUnknown_recordsPostPublishFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");
        EventPublishOutcomeUnknownException exception = new EventPublishOutcomeUnknownException(
                "Kafka publish outcome unknown",
                new RuntimeException("timeout")
        );

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));
        doThrow(exception).when(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markPublished(any());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("Kafka 발행 결과가 불명확한 경우 일반 publish 실패로 기록하지 않는다")
    void relay_publishOutcomeUnknown_doesNotRecordRetryableFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));

        doThrow(new EventPublishOutcomeUnknownException(
                "Kafka publish outcome unknown",
                new RuntimeException("timeout")
        )).when(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), anyString());
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");
    }

    @Test
    @DisplayName("Kafka 발행 자체가 실패하면 재시도 가능한 실패로 기록한다")
    void relay_publishFailure_recordsFailedAttempt() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));
        doThrow(new IllegalStateException("Kafka unavailable")).when(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        verify(problemEventOutboxPersistenceService).recordFailedAttempt(outbox.getId(), "KAFKA_PUBLISH_FAILED:IllegalStateException");
        verify(problemEventOutboxPersistenceService, never()).recordPostPublishFailure(any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markPublished(any());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 예외 타입을 실패 사유에 포함한다")
    void relay_publishFailure_recordsExceptionType() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));
        doThrow(new UnsupportedOperationException("publish failed")).when(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordFailedAttempt(outbox.getId(), "KAFKA_PUBLISH_FAILED:UnsupportedOperationException");
    }

    @Test
    @DisplayName("Kafka 발행 성공 후 PUBLISHED 저장에 실패하면 post publish 실패를 기록한다")
    void relay_markPublishedFails_recordsPostPublishFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).markPublished(outbox.getId());

        // when
        problemEventRelayFacade.relay();

        // then
        InOrder inOrder = inOrder(eventPublisherPort, problemEventOutboxPersistenceService);

        inOrder.verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        inOrder.verify(problemEventOutboxPersistenceService).markPublished(outbox.getId());
        inOrder.verify(problemEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), "OUTBOX_POST_PUBLISH_FAILURE");

        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("PUBLISHED 저장 실패는 Kafka publish 실패로 잘못 기록하지 않는다")
    void relay_markPublishedFails_doesNotRecordKafkaPublishFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).markPublished(outbox.getId());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), anyString());
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), "OUTBOX_POST_PUBLISH_FAILURE");
    }

    @Test
    @DisplayName("여러 Outbox 발행에 성공하면 각 Outbox를 한 번씩 발행하고 PUBLISHED 처리한다")
    void relay_multipleOutboxes_publishesAll() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");
        ProblemEventOutbox third = createOutbox("{\"problemId\":\"third\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(first, second, third));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(first.getId());

        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId());

        verify(eventPublisherPort).publish(TOPIC, third.getAggregateId().toString(), third.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(third.getId());

        verify(eventPublisherPort, times(3)).publish(anyString(), anyString(), anyString());
        verify(problemEventOutboxPersistenceService, times(3)).markPublished(any());
    }

    @Test
    @DisplayName("첫 번째 Outbox의 Kafka 발행이 실패해도 다음 Outbox를 계속 발행한다")
    void relay_firstPublishFails_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("Kafka unavailable")).when(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordFailedAttempt(first.getId(), "KAFKA_PUBLISH_FAILED:IllegalStateException");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId());
        verify(problemEventOutboxPersistenceService, never()).markPublished(first.getId());
    }

    @Test
    @DisplayName("첫 번째 Outbox의 Kafka 발행 결과가 불명확해도 다음 Outbox를 계속 발행한다")
    void relay_firstPublishOutcomeUnknown_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(first, second));

        doThrow(new EventPublishOutcomeUnknownException(
                "Kafka publish outcome unknown",
                new RuntimeException("timeout")
        )).when(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(first.getId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId());
    }

    @Test
    @DisplayName("첫 번째 Outbox의 PUBLISHED 저장이 실패해도 다음 Outbox를 계속 발행한다")
    void relay_firstMarkPublishedFails_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).markPublished(first.getId());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(first.getId(), "OUTBOX_POST_PUBLISH_FAILURE");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId());
    }

    @Test
    @DisplayName("payload 초과 Outbox가 있어도 다음 정상 Outbox를 계속 처리한다")
    void relay_payloadTooLarge_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("a".repeat(MAX_PAYLOAD_BYTES + 1));
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(first, second));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).markFailed(first.getId(), "EVENT_PAYLOAD_TOO_LARGE");
        verify(eventPublisherPort, never()).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId());
    }

    @Test
    @DisplayName("payload 초과 Outbox를 FAILED 처리하는 중 예외가 발생해도 다음 Outbox를 계속 처리한다")
    void relay_markFailedThrows_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("a".repeat(MAX_PAYLOAD_BYTES + 1));
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).markFailed(first.getId(), "EVENT_PAYLOAD_TOO_LARGE");

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).markFailed(first.getId(), "EVENT_PAYLOAD_TOO_LARGE");
        verify(eventPublisherPort, never()).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId());
    }

    @Test
    @DisplayName("Kafka 실패 기록 중 예외가 발생해도 다음 Outbox를 계속 처리한다")
    void relay_recordFailedAttemptThrows_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("Kafka unavailable")).when(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).recordFailedAttempt(first.getId(), "KAFKA_PUBLISH_FAILED:IllegalStateException");

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordFailedAttempt(first.getId(), "KAFKA_PUBLISH_FAILED:IllegalStateException");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId());
    }

    @Test
    @DisplayName("post publish 실패 기록 중 예외가 발생해도 다음 Outbox를 계속 처리한다")
    void relay_recordPostPublishFailureThrows_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(first, second));

        doThrow(new EventPublishOutcomeUnknownException(
                "Kafka publish outcome unknown",
                new RuntimeException("timeout")
        )).when(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());

        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService)
                .recordPostPublishFailure(first.getId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(first.getId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId());
    }

    @Test
    @DisplayName("각 Outbox는 자신의 aggregateId를 Kafka message key로 사용한다")
    void relay_usesAggregateIdAsKafkaMessageKey() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
    }

    @Test
    @DisplayName("Kafka 발행 시 설정된 problem published topic을 사용한다")
    void relay_usesConfiguredTopic() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
    }

    @Test
    @DisplayName("정상 발행 완료 시 publish와 markPublished 외 실패 처리 메서드를 호출하지 않는다")
    void relay_publishSuccess_doesNotInvokeFailureHandlers() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        when(problemEventOutboxRepository.findPollableByStatus(ProblemEventOutboxStatus.PENDING, BATCH_SIZE)).thenReturn(List.of(outbox));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(outbox.getId());
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).recordPostPublishFailure(any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), anyString());
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