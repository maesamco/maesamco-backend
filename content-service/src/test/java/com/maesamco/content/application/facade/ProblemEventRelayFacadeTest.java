package com.maesamco.content.application.facade;

import com.maesamco.content.application.persistence_service.ProblemEventOutboxPersistenceService;
import com.maesamco.content.application.port.EventPublishOutcomeUnknownException;
import com.maesamco.content.application.port.EventPublisherPort;
import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
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
    private static final Duration LEASE_DURATION = Duration.ofSeconds(60);
    private static final long PUBLISH_TIMEOUT_MS = 5_000L;

    @Mock
    private ProblemEventOutboxPersistenceService problemEventOutboxPersistenceService;

    @Mock
    private EventPublisherPort eventPublisherPort;

    private ProblemEventRelayFacade problemEventRelayFacade;

    @BeforeEach
    void setUp() {
        problemEventRelayFacade = createFacade(BATCH_SIZE);
    }

    private ProblemEventRelayFacade createFacade(int batchSize) {
        return new ProblemEventRelayFacade(
                problemEventOutboxPersistenceService,
                eventPublisherPort,
                TOPIC,
                batchSize,
                MAX_PAYLOAD_BYTES,
                LEASE_DURATION.toMillis(),
                PUBLISH_TIMEOUT_MS
        );
    }

    /** claimNext가 주어진 Outbox를 순서대로 반환한 뒤 빈 결과를 반환하도록 설정합니다. */
    private void givenClaimed(ProblemEventOutbox... outboxes) {
        OngoingStubbing<Optional<ProblemEventOutbox>> stubbing =
                when(problemEventOutboxPersistenceService.claimNext(any(UUID.class), eq(LEASE_DURATION)));

        for (ProblemEventOutbox outbox : outboxes) {
            stubbing = stubbing.thenReturn(Optional.of(outbox));
        }

        stubbing.thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("선점할 Outbox가 없으면 이벤트를 발행하지 않는다")
    void relay_noClaimableOutbox_doesNothing() {
        // given
        givenClaimed();

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService, times(1)).claimNext(any(UUID.class), eq(LEASE_DURATION));
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    @DisplayName("Relay는 한 번의 폴링에서 batch size를 넘겨 선점하지 않는다")
    void relay_claimsAtMostBatchSize() {
        // given
        ProblemEventRelayFacade smallBatchFacade = createFacade(2);
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");
        ProblemEventOutbox third = createOutbox("{\"problemId\":\"third\"}");

        givenClaimed(first, second, third);

        // when
        smallBatchFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService, times(2)).claimNext(any(UUID.class), eq(LEASE_DURATION));
        verify(eventPublisherPort, never()).publish(TOPIC, third.getAggregateId().toString(), third.getPayload());
    }

    @Test
    @DisplayName("선점할 때마다 새로운 claimId를 사용한다")
    void relay_usesNewClaimIdPerClaim() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        givenClaimed(first);

        ArgumentCaptor<UUID> claimIdCaptor = ArgumentCaptor.forClass(UUID.class);

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService, times(2)).claimNext(claimIdCaptor.capture(), eq(LEASE_DURATION));
        assertThat(claimIdCaptor.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("선점 중 예외가 발생하면 이번 폴링을 중단하고 발행하지 않는다")
    void relay_claimThrows_stopsPolling() {
        // given
        when(problemEventOutboxPersistenceService.claimNext(any(UUID.class), eq(LEASE_DURATION)))
                .thenThrow(new IllegalStateException("database error"));

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService, times(1)).claimNext(any(UUID.class), eq(LEASE_DURATION));
        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    @DisplayName("lease가 Kafka ACK 대기 시간 + 1초보다 짧으면 Facade 생성에 실패한다")
    void constructor_leaseShorterThanPublishTimeout_throws() {
        assertThatThrownBy(() -> new ProblemEventRelayFacade(
                problemEventOutboxPersistenceService,
                eventPublisherPort,
                TOPIC,
                BATCH_SIZE,
                MAX_PAYLOAD_BYTES,
                5_500L,
                5_000L
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("선점한 Outbox 발행에 성공하면 Kafka 발행 후 PUBLISHED 상태로 변경한다")
    void relay_publishSuccess_marksPublished() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        givenClaimed(outbox);

        // when
        problemEventRelayFacade.relay();

        // then
        InOrder inOrder = inOrder(eventPublisherPort, problemEventOutboxPersistenceService);

        inOrder.verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        inOrder.verify(problemEventOutboxPersistenceService).markPublished(outbox.getId(), outbox.getClaimId());

        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).recordPostPublishFailure(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), any(), anyString());
    }

    @Test
    @DisplayName("payload 크기가 최대 허용 크기와 정확히 같으면 Kafka로 발행한다")
    void relay_payloadExactlyMaxSize_publishesSuccessfully() {
        // given
        String payload = "a".repeat(MAX_PAYLOAD_BYTES);
        ProblemEventOutbox outbox = createOutbox(payload);

        givenClaimed(outbox);

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), payload);
        verify(problemEventOutboxPersistenceService).markPublished(outbox.getId(), outbox.getClaimId());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), any(), anyString());
    }

    @Test
    @DisplayName("payload가 최대 허용 크기를 초과하면 Kafka로 발행하지 않고 FAILED 처리한다")
    void relay_payloadTooLarge_marksFailedWithoutPublishing() {
        // given
        String oversizedPayload = "a".repeat(MAX_PAYLOAD_BYTES + 1);
        ProblemEventOutbox outbox = createOutbox(oversizedPayload);

        givenClaimed(outbox);

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).markFailed(outbox.getId(), outbox.getClaimId(), "EVENT_PAYLOAD_TOO_LARGE");
        verifyNoInteractions(eventPublisherPort);
        verify(problemEventOutboxPersistenceService, never()).markPublished(any(), any());
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).recordPostPublishFailure(any(), any(), anyString());
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

        givenClaimed(outbox);
        doThrow(exception).when(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), outbox.getClaimId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markPublished(any(), any());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), any(), anyString());
    }

    @Test
    @DisplayName("Kafka 발행 결과가 불명확한 경우 일반 publish 실패로 기록하지 않는다")
    void relay_publishOutcomeUnknown_doesNotRecordRetryableFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        givenClaimed(outbox);

        doThrow(new EventPublishOutcomeUnknownException(
                "Kafka publish outcome unknown",
                new RuntimeException("timeout")
        )).when(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), outbox.getClaimId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");
    }

    @Test
    @DisplayName("Kafka 발행 자체가 실패하면 재시도 가능한 실패로 기록한다")
    void relay_publishFailure_recordsFailedAttempt() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        givenClaimed(outbox);
        doThrow(new IllegalStateException("Kafka unavailable")).when(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        verify(problemEventOutboxPersistenceService).recordFailedAttempt(outbox.getId(), outbox.getClaimId(), "KAFKA_PUBLISH_FAILED:IllegalStateException");
        verify(problemEventOutboxPersistenceService, never()).recordPostPublishFailure(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markPublished(any(), any());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), any(), anyString());
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 예외 타입을 실패 사유에 포함한다")
    void relay_publishFailure_recordsExceptionType() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        givenClaimed(outbox);
        doThrow(new UnsupportedOperationException("publish failed")).when(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordFailedAttempt(outbox.getId(), outbox.getClaimId(), "KAFKA_PUBLISH_FAILED:UnsupportedOperationException");
    }

    @Test
    @DisplayName("Kafka 발행 성공 후 PUBLISHED 저장에 실패하면 post publish 실패를 기록한다")
    void relay_markPublishedFails_recordsPostPublishFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        givenClaimed(outbox);
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).markPublished(outbox.getId(), outbox.getClaimId());

        // when
        problemEventRelayFacade.relay();

        // then
        InOrder inOrder = inOrder(eventPublisherPort, problemEventOutboxPersistenceService);

        inOrder.verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        inOrder.verify(problemEventOutboxPersistenceService).markPublished(outbox.getId(), outbox.getClaimId());
        inOrder.verify(problemEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), outbox.getClaimId(), "OUTBOX_POST_PUBLISH_FAILURE");

        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), any(), anyString());
    }

    @Test
    @DisplayName("PUBLISHED 저장 실패는 Kafka publish 실패로 잘못 기록하지 않는다")
    void relay_markPublishedFails_doesNotRecordKafkaPublishFailure() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        givenClaimed(outbox);
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).markPublished(outbox.getId(), outbox.getClaimId());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), outbox.getClaimId(), "OUTBOX_POST_PUBLISH_FAILURE");
    }

    @Test
    @DisplayName("여러 Outbox 발행에 성공하면 각 Outbox를 한 번씩 발행하고 PUBLISHED 처리한다")
    void relay_multipleOutboxes_publishesAll() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");
        ProblemEventOutbox third = createOutbox("{\"problemId\":\"third\"}");

        givenClaimed(first, second, third);

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(first.getId(), first.getClaimId());

        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId(), second.getClaimId());

        verify(eventPublisherPort).publish(TOPIC, third.getAggregateId().toString(), third.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(third.getId(), third.getClaimId());

        verify(eventPublisherPort, times(3)).publish(anyString(), anyString(), anyString());
        verify(problemEventOutboxPersistenceService, times(3)).markPublished(any(), any());
    }

    @Test
    @DisplayName("첫 번째 Outbox의 Kafka 발행이 실패해도 다음 Outbox를 계속 발행한다")
    void relay_firstPublishFails_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        givenClaimed(first, second);
        doThrow(new IllegalStateException("Kafka unavailable")).when(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordFailedAttempt(first.getId(), first.getClaimId(), "KAFKA_PUBLISH_FAILED:IllegalStateException");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId(), second.getClaimId());
        verify(problemEventOutboxPersistenceService, never()).markPublished(first.getId(), first.getClaimId());
    }

    @Test
    @DisplayName("첫 번째 Outbox의 Kafka 발행 결과가 불명확해도 다음 Outbox를 계속 발행한다")
    void relay_firstPublishOutcomeUnknown_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        givenClaimed(first, second);

        doThrow(new EventPublishOutcomeUnknownException(
                "Kafka publish outcome unknown",
                new RuntimeException("timeout")
        )).when(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(first.getId(), first.getClaimId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId(), second.getClaimId());
    }

    @Test
    @DisplayName("첫 번째 Outbox의 PUBLISHED 저장이 실패해도 다음 Outbox를 계속 발행한다")
    void relay_firstMarkPublishedFails_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        givenClaimed(first, second);
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).markPublished(first.getId(), first.getClaimId());

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(first.getId(), first.getClaimId(), "OUTBOX_POST_PUBLISH_FAILURE");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId(), second.getClaimId());
    }

    @Test
    @DisplayName("payload 초과 Outbox가 있어도 다음 정상 Outbox를 계속 처리한다")
    void relay_payloadTooLarge_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("a".repeat(MAX_PAYLOAD_BYTES + 1));
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        givenClaimed(first, second);

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).markFailed(first.getId(), first.getClaimId(), "EVENT_PAYLOAD_TOO_LARGE");
        verify(eventPublisherPort, never()).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId(), second.getClaimId());
    }

    @Test
    @DisplayName("payload 초과 Outbox를 FAILED 처리하는 중 예외가 발생해도 다음 Outbox를 계속 처리한다")
    void relay_markFailedThrows_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("a".repeat(MAX_PAYLOAD_BYTES + 1));
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        givenClaimed(first, second);
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).markFailed(first.getId(), first.getClaimId(), "EVENT_PAYLOAD_TOO_LARGE");

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).markFailed(first.getId(), first.getClaimId(), "EVENT_PAYLOAD_TOO_LARGE");
        verify(eventPublisherPort, never()).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId(), second.getClaimId());
    }

    @Test
    @DisplayName("Kafka 실패 기록 중 예외가 발생해도 다음 Outbox를 계속 처리한다")
    void relay_recordFailedAttemptThrows_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        givenClaimed(first, second);
        doThrow(new IllegalStateException("Kafka unavailable")).when(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());
        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService).recordFailedAttempt(first.getId(), first.getClaimId(), "KAFKA_PUBLISH_FAILED:IllegalStateException");

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordFailedAttempt(first.getId(), first.getClaimId(), "KAFKA_PUBLISH_FAILED:IllegalStateException");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId(), second.getClaimId());
    }

    @Test
    @DisplayName("post publish 실패 기록 중 예외가 발생해도 다음 Outbox를 계속 처리한다")
    void relay_recordPostPublishFailureThrows_continuesNextOutbox() {
        // given
        ProblemEventOutbox first = createOutbox("{\"problemId\":\"first\"}");
        ProblemEventOutbox second = createOutbox("{\"problemId\":\"second\"}");

        givenClaimed(first, second);

        doThrow(new EventPublishOutcomeUnknownException(
                "Kafka publish outcome unknown",
                new RuntimeException("timeout")
        )).when(eventPublisherPort).publish(TOPIC, first.getAggregateId().toString(), first.getPayload());

        doThrow(new IllegalStateException("database error")).when(problemEventOutboxPersistenceService)
                .recordPostPublishFailure(first.getId(), first.getClaimId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");

        // when
        problemEventRelayFacade.relay();

        // then
        verify(problemEventOutboxPersistenceService).recordPostPublishFailure(first.getId(), first.getClaimId(), "KAFKA_PUBLISH_OUTCOME_UNKNOWN");
        verify(eventPublisherPort).publish(TOPIC, second.getAggregateId().toString(), second.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(second.getId(), second.getClaimId());
    }

    @Test
    @DisplayName("각 Outbox는 자신의 aggregateId를 Kafka message key로 사용한다")
    void relay_usesAggregateIdAsKafkaMessageKey() {
        // given
        ProblemEventOutbox outbox = createOutbox("{\"problemId\":\"test\"}");

        givenClaimed(outbox);

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

        givenClaimed(outbox);

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

        givenClaimed(outbox);

        // when
        problemEventRelayFacade.relay();

        // then
        verify(eventPublisherPort).publish(TOPIC, outbox.getAggregateId().toString(), outbox.getPayload());
        verify(problemEventOutboxPersistenceService).markPublished(outbox.getId(), outbox.getClaimId());
        verify(problemEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).recordPostPublishFailure(any(), any(), anyString());
        verify(problemEventOutboxPersistenceService, never()).markFailed(any(), any(), anyString());
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

        // Relay는 선점(claimNext)된 Outbox만 처리하므로 IN_PROGRESS 상태로 준비합니다.
        Instant now = Instant.now();
        outbox.claim(UUID.randomUUID(), now, now.plus(LEASE_DURATION));

        return outbox;
    }
}