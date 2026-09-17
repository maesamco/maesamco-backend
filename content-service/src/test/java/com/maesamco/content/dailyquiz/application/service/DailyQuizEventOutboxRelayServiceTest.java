package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPublishOutcomeUnknownException;
import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPublisherPort;
import com.maesamco.content.application.dailyquiz.service.DailyQuizEventOutboxRelayService;
import com.maesamco.content.application.dailyquiz.service.DailyQuizEventOutboxStatusService;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyQuizEventOutboxRelayServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-17T00:00:00Z");

    private static final int BATCH_SIZE = 10;
    private static final int MAX_PAYLOAD_BYTES = 100;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long BACKOFF_BASE_MILLIS = 1_000L;
    private static final long BACKOFF_MAX_MILLIS = 8_000L;

    @Mock
    private DailyQuizEventOutboxRepository outboxRepository;

    @Mock
    private DailyQuizCompletedEventPublisherPort eventPublisherPort;

    @Mock
    private DailyQuizEventOutboxStatusService statusService;

    private DailyQuizEventOutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new DailyQuizEventOutboxRelayService(
                outboxRepository,
                eventPublisherPort,
                statusService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                BATCH_SIZE,
                MAX_PAYLOAD_BYTES,
                MAX_RETRY_COUNT,
                BACKOFF_BASE_MILLIS,
                BACKOFF_MAX_MILLIS
        );
    }

    @Test
    @DisplayName("발행 가능한 Outbox를 조회해 Kafka ACK 확인 후 PUBLISHED로 변경한다")
    void relayPendingOutboxes_publishesAndRecordsSuccess() {
        DailyQuizEventOutbox outbox = createPendingOutbox("payload");
        when(outboxRepository.findPublishablePending(NOW, BATCH_SIZE))
                .thenReturn(List.of(outbox));

        relayService.relayPendingOutboxes();

        var ordered = inOrder(eventPublisherPort, statusService);
        ordered.verify(eventPublisherPort).publish(
                outbox.getAggregateId(),
                outbox.getPayload()
        );
        ordered.verify(statusService).recordPublishSuccess(
                outbox.getId(),
                NOW
        );
    }

    @Test
    @DisplayName("UTF-8 payload 크기가 제한을 넘으면 Kafka에 보내지 않고 즉시 실패 처리한다")
    void relayPendingOutboxes_rejectsOversizedPayload() {
        DailyQuizEventOutboxRelayService smallPayloadRelayService =
                new DailyQuizEventOutboxRelayService(
                        outboxRepository,
                        eventPublisherPort,
                        statusService,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        BATCH_SIZE,
                        5,
                        MAX_RETRY_COUNT,
                        BACKOFF_BASE_MILLIS,
                        BACKOFF_MAX_MILLIS
                );
        DailyQuizEventOutbox outbox = createPendingOutbox("가가");
        when(outboxRepository.findPublishablePending(NOW, BATCH_SIZE))
                .thenReturn(List.of(outbox));

        smallPayloadRelayService.relayPendingOutboxes();

        verify(eventPublisherPort, never()).publish(any(), anyString());
        verify(statusService).recordUnrecoverablePublishFailure(
                outbox.getId(),
                "EVENT_PAYLOAD_TOO_LARGE"
        );
    }

    @Test
    @DisplayName("확정적인 Kafka 발행 실패는 재시도 횟수와 지수 백오프 시각을 기록한다")
    void relayPendingOutboxes_recordsConfirmedPublishFailure() {
        DailyQuizEventOutbox outbox = createPendingOutbox("payload");
        when(outboxRepository.findPublishablePending(NOW, BATCH_SIZE))
                .thenReturn(List.of(outbox));
        doThrow(new IllegalStateException(
                "publish failed",
                new IllegalArgumentException("broker rejected record")
        )).when(eventPublisherPort).publish(
                outbox.getAggregateId(),
                outbox.getPayload()
        );

        relayService.relayPendingOutboxes();

        verify(statusService).recordPublishFailure(
                outbox.getId(),
                "KAFKA_PUBLISH_FAILED:IllegalArgumentException",
                MAX_RETRY_COUNT,
                NOW.plusMillis(BACKOFF_BASE_MILLIS)
        );
        verify(statusService, never()).recordPublishSuccess(any(), any());
    }

    @Test
    @DisplayName("Kafka 발행 결과를 확인할 수 없으면 FAILED 상한 없이 다음 시도를 예약한다")
    void relayPendingOutboxes_recordsUnknownPublishOutcome() {
        DailyQuizEventOutbox outbox = createPendingOutbox("payload");
        when(outboxRepository.findPublishablePending(NOW, BATCH_SIZE))
                .thenReturn(List.of(outbox));
        doThrow(new DailyQuizCompletedEventPublishOutcomeUnknownException(
                "outcome unknown",
                new java.util.concurrent.TimeoutException()
        )).when(eventPublisherPort).publish(
                outbox.getAggregateId(),
                outbox.getPayload()
        );

        relayService.relayPendingOutboxes();

        verify(statusService).recordPublishOutcomeUnknown(
                outbox.getId(),
                "KAFKA_PUBLISH_OUTCOME_UNKNOWN",
                NOW.plusMillis(BACKOFF_BASE_MILLIS)
        );
        verify(statusService, never()).recordPublishFailure(
                any(),
                anyString(),
                any(Integer.class),
                any()
        );
    }

    @Test
    @DisplayName("인터럽트된 발행 결과를 기록한 뒤 남은 Outbox 처리를 중단한다")
    void relayPendingOutboxes_stopsBatchWhenInterrupted() {
        DailyQuizEventOutbox first = createPendingOutbox("first");
        DailyQuizEventOutbox second = createPendingOutbox("second");
        when(outboxRepository.findPublishablePending(NOW, BATCH_SIZE))
                .thenReturn(List.of(first, second));
        doThrow(new DailyQuizCompletedEventPublishOutcomeUnknownException(
                "interrupted",
                new InterruptedException()
        )).when(eventPublisherPort).publish(
                first.getAggregateId(),
                first.getPayload()
        );

        Thread.currentThread().interrupt();
        try {
            relayService.relayPendingOutboxes();

            verify(eventPublisherPort, never()).publish(
                    second.getAggregateId(),
                    second.getPayload()
            );
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Kafka ACK 후 성공 상태 저장에 실패하면 발행 실패로 바꾸지 않고 재시도를 예약한다")
    void relayPendingOutboxes_whenSuccessStateUpdateFails_keepsRetryable() {
        DailyQuizEventOutbox outbox = createPendingOutbox("payload");
        when(outboxRepository.findPublishablePending(NOW, BATCH_SIZE))
                .thenReturn(List.of(outbox));
        doThrow(new IllegalStateException("database unavailable"))
                .when(statusService)
                .recordPublishSuccess(outbox.getId(), NOW);

        relayService.relayPendingOutboxes();

        verify(statusService).recordPublishOutcomeUnknown(
                outbox.getId(),
                "KAFKA_PUBLISHED_STATUS_UPDATE_FAILED",
                NOW.plusMillis(BACKOFF_BASE_MILLIS)
        );
        verify(statusService, never()).recordPublishFailure(
                any(),
                anyString(),
                any(Integer.class),
                any()
        );
    }

    @Test
    @DisplayName("한 Outbox의 실패 상태 저장이 실패해도 다음 Outbox는 계속 처리한다")
    void relayPendingOutboxes_isolatesEachOutboxFailure() {
        DailyQuizEventOutbox first = createPendingOutbox("first");
        DailyQuizEventOutbox second = createPendingOutbox("second");
        when(outboxRepository.findPublishablePending(NOW, BATCH_SIZE))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("publish failed"))
                .when(eventPublisherPort)
                .publish(first.getAggregateId(), first.getPayload());
        doThrow(new IllegalStateException("database unavailable"))
                .when(statusService)
                .recordPublishFailure(
                        first.getId(),
                        "KAFKA_PUBLISH_FAILED:IllegalStateException",
                        MAX_RETRY_COUNT,
                        NOW.plusMillis(BACKOFF_BASE_MILLIS)
                );

        relayService.relayPendingOutboxes();

        verify(eventPublisherPort).publish(
                second.getAggregateId(),
                second.getPayload()
        );
        verify(statusService).recordPublishSuccess(
                second.getId(),
                NOW
        );
    }

    @Test
    @DisplayName("지수 백오프는 설정된 최대 시간을 넘지 않는다")
    void relayPendingOutboxes_capsExponentialBackoff() {
        DailyQuizEventOutbox outbox = createPendingOutbox("payload");
        for (int count = 0; count < 10; count++) {
            outbox.recordPublishOutcomeUnknown(
                    "PREVIOUS_OUTCOME_UNKNOWN",
                    NOW.plusSeconds(count + 1L)
            );
        }
        when(outboxRepository.findPublishablePending(NOW, BATCH_SIZE))
                .thenReturn(List.of(outbox));
        doThrow(new IllegalStateException("publish failed"))
                .when(eventPublisherPort)
                .publish(outbox.getAggregateId(), outbox.getPayload());

        relayService.relayPendingOutboxes();

        verify(statusService).recordPublishFailure(
                outbox.getId(),
                "KAFKA_PUBLISH_FAILED:IllegalStateException",
                MAX_RETRY_COUNT,
                NOW.plusMillis(BACKOFF_MAX_MILLIS)
        );
    }

    private DailyQuizEventOutbox createPendingOutbox(String payload) {
        DailyQuizEventOutbox outbox =
                DailyQuizEventOutbox.createPending(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "DAILY_QUIZ_COMPLETED",
                        1,
                        payload,
                        NOW.minusSeconds(60)
                );

        ReflectionTestUtils.setField(
                outbox,
                "id",
                UUID.randomUUID()
        );

        return outbox;
    }
}
