package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.persistence_service.CoachingEventOutboxPersistenceService;
import com.maesamco.coaching.application.port.EventPublishOutcomeUnknownException;
import com.maesamco.coaching.application.port.EventPublisherPort;
import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoachingEventRelayFacadeTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String TOPIC = "coaching-completed";
    private static final long LEASE_DURATION_MILLIS = 300_000L;
    private static final long PUBLISH_TIMEOUT_MILLIS = 3_000L;

    @Mock
    private CoachingEventOutboxRepository coachingEventOutboxRepository;

    @Mock
    private CoachingEventOutboxPersistenceService coachingEventOutboxPersistenceService;

    @Mock
    private EventPublisherPort eventPublisherPort;

    private CoachingEventRelayFacade coachingEventRelayFacade;

    // 이슈 #261 — 생성자가 @Value로 주입되는 lease/timeout 값을 검증하는 원시 타입
    // 파라미터를 갖게 되면서 @InjectMocks(모킹 불가능한 타입은 0/null로 채움)로는 검증을
    // 통과 못 한다 — DailyQuizEventOutboxRelayServiceTest와 동일한 이유로 직접 생성한다.
    @BeforeEach
    void setUp() {
        coachingEventRelayFacade = new CoachingEventRelayFacade(
                coachingEventOutboxRepository,
                coachingEventOutboxPersistenceService,
                eventPublisherPort,
                TOPIC,
                LEASE_DURATION_MILLIS,
                PUBLISH_TIMEOUT_MILLIS
        );
    }

    private CoachingEventOutbox pendingOutbox() {
        ObjectNode payload = JSON_MAPPER.createObjectNode().put("coachingId", UUID.randomUUID().toString());
        return CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload);
    }

    private void stubClaimable(CoachingEventOutbox... outboxes) {
        given(coachingEventOutboxRepository.claimPublishable(any(Instant.class), any(Instant.class), any(UUID.class), eq(100)))
                .willReturn(List.of(outboxes));
    }

    @Nested
    @DisplayName("생성자")
    class Constructor {

        @Test
        @DisplayName("lease-duration-ms가 publish-timeout-ms보다 안전 마진(1000ms) 이상 길지 않으면 즉시 실패한다")
        void rejectsInsufficientLeaseSafetyMargin() {
            assertThatThrownBy(() -> new CoachingEventRelayFacade(
                    coachingEventOutboxRepository, coachingEventOutboxPersistenceService, eventPublisherPort,
                    TOPIC, PUBLISH_TIMEOUT_MILLIS + 500, PUBLISH_TIMEOUT_MILLIS
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("relay")
    class Relay {

        @Test
        @DisplayName("선점한 Outbox 발행에 성공하면 markPublished를 호출한다")
        void marksPublishedOnSuccess() {
            CoachingEventOutbox outbox = pendingOutbox();
            stubClaimable(outbox);

            coachingEventRelayFacade.relay();

            verify(eventPublisherPort).publish(
                    eq(TOPIC), eq(outbox.getAggregateId().toString()), eq(JSON_MAPPER.writeValueAsString(outbox.getPayload())));
            verify(coachingEventOutboxPersistenceService).markPublished(eq(outbox.getId()), any(UUID.class));
            verify(coachingEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any());
        }

        @Test
        @DisplayName("발행이 실패하면 recordFailedAttempt를 호출하고 예외를 전파하지 않는다")
        void recordsFailedAttemptOnPublishFailure() {
            CoachingEventOutbox outbox = pendingOutbox();
            stubClaimable(outbox);
            willThrow(new IllegalStateException("Kafka 발행 실패"))
                    .given(eventPublisherPort).publish(anyString(), anyString(), anyString());

            coachingEventRelayFacade.relay();

            verify(coachingEventOutboxPersistenceService).recordFailedAttempt(eq(outbox.getId()), any(UUID.class));
            verify(coachingEventOutboxPersistenceService, never()).markPublished(any(), any());
        }

        @Test
        @DisplayName("발행은 성공했지만 markPublished 후처리가 실패하면 recordPostPublishFailure로 넘긴다")
        void recordsPostPublishFailureWhenMarkPublishedFails() {
            CoachingEventOutbox outbox = pendingOutbox();
            stubClaimable(outbox);
            willThrow(new RuntimeException("DB 후처리 실패"))
                    .given(coachingEventOutboxPersistenceService).markPublished(eq(outbox.getId()), any(UUID.class));

            coachingEventRelayFacade.relay();

            verify(eventPublisherPort).publish(anyString(), anyString(), anyString());
            verify(coachingEventOutboxPersistenceService).markPublished(eq(outbox.getId()), any(UUID.class));
            verify(coachingEventOutboxPersistenceService).recordPostPublishFailure(eq(outbox.getId()), any(UUID.class));
            verify(coachingEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any());
        }

        @Test
        @DisplayName("발행 결과를 확인 못하면(EventPublishOutcomeUnknownException) recordFailedAttempt가 아니라 recordPostPublishFailure로 보낸다")
        void recordsPostPublishFailureWhenPublishOutcomeUnknown() {
            CoachingEventOutbox outbox = pendingOutbox();
            stubClaimable(outbox);
            willThrow(new EventPublishOutcomeUnknownException("응답 대기 시간 초과", new java.util.concurrent.TimeoutException()))
                    .given(eventPublisherPort).publish(anyString(), anyString(), anyString());

            coachingEventRelayFacade.relay();

            verify(coachingEventOutboxPersistenceService).recordPostPublishFailure(eq(outbox.getId()), any(UUID.class));
            verify(coachingEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any());
            verify(coachingEventOutboxPersistenceService, never()).markPublished(any(), any());
        }

        @Test
        @DisplayName("배치 중 한 항목의 처리가 예외를 던져도 나머지 항목은 독립적으로 계속 처리한다")
        void isolatesFailureOfOneItemFromRestOfBatch() {
            CoachingEventOutbox brokenOutbox = pendingOutbox();
            CoachingEventOutbox healthyOutbox = pendingOutbox();
            stubClaimable(brokenOutbox, healthyOutbox);

            // 첫 번째 outbox는 발행도 실패하고, 그 실패를 기록하려는 recordFailedAttempt 자체도
            // 예외를 던진다(예: DB 커넥션 풀 고갈) — relayOne() 밖으로 예외가 전파되는 상황을 흉내냄.
            willThrow(new IllegalStateException("Kafka 발행 실패"))
                    .given(eventPublisherPort).publish(eq(TOPIC), eq(brokenOutbox.getAggregateId().toString()), anyString());
            willThrow(new RuntimeException("DB 커넥션 풀 고갈"))
                    .given(coachingEventOutboxPersistenceService).recordFailedAttempt(eq(brokenOutbox.getId()), any(UUID.class));

            coachingEventRelayFacade.relay();

            verify(eventPublisherPort).publish(eq(TOPIC), eq(healthyOutbox.getAggregateId().toString()), anyString());
            verify(coachingEventOutboxPersistenceService).markPublished(eq(healthyOutbox.getId()), any(UUID.class));
        }

        @Test
        @DisplayName("첫 항목 처리 중 인터럽트가 감지되면 나머지 배치는 처리하지 않고 즉시 멈춘다")
        void stopsBatchWhenInterrupted() {
            CoachingEventOutbox firstOutbox = pendingOutbox();
            CoachingEventOutbox secondOutbox = pendingOutbox();
            stubClaimable(firstOutbox, secondOutbox);
            // KafkaEventPublisherAdapter가 InterruptedException을 잡아 interrupt 플래그를
            // 복원한 뒤 EventPublishOutcomeUnknownException으로 감싸 올리는 상황을 흉내낸다.
            willAnswer(invocation -> {
                Thread.currentThread().interrupt();
                throw new EventPublishOutcomeUnknownException("인터럽트됨", new InterruptedException());
            }).given(eventPublisherPort).publish(
                    eq(TOPIC), eq(firstOutbox.getAggregateId().toString()), anyString());

            try {
                coachingEventRelayFacade.relay();

                verify(eventPublisherPort, never()).publish(
                        eq(TOPIC), eq(secondOutbox.getAggregateId().toString()), anyString());
                verify(coachingEventOutboxPersistenceService, never()).recordFailedAttempt(eq(secondOutbox.getId()), any());
                verify(coachingEventOutboxPersistenceService, never()).markPublished(eq(secondOutbox.getId()), any());
            } finally {
                Thread.interrupted(); // 이 테스트 스레드의 interrupt 플래그를 정리해서 다음 테스트에 영향 안 주게 함
            }
        }
    }
}
