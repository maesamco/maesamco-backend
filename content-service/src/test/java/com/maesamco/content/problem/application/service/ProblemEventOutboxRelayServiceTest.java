package com.maesamco.content.problem.application.service;

import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import com.maesamco.content.problem.infrastructure.messaging.producer.ProblemPublishedKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemEventOutboxRelayServiceTest {

    private static final int BATCH_SIZE = 50;
    private static final long PUBLISH_TIMEOUT_MILLIS = 5_000L;

    @Mock
    private ProblemEventOutboxRepository outboxRepository;

    @Mock
    private ProblemPublishedKafkaProducer kafkaProducer;

    @Mock
    private ProblemEventOutboxStatusService statusService;

    private ProblemEventOutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService =
                new ProblemEventOutboxRelayService(
                        outboxRepository,
                        kafkaProducer,
                        statusService,
                        BATCH_SIZE,
                        PUBLISH_TIMEOUT_MILLIS
                );
    }

    @Test
    @DisplayName(
            "PENDING Outbox를 Kafka에 발행하고 ACK 성공 시 PUBLISHED 처리한다"
    )
    void relayPendingOutboxes_marksPublished_whenKafkaPublishSucceeds() {
        // given
        UUID outboxId =
                UUID.randomUUID();

        UUID problemId =
                UUID.randomUUID();

        ProblemEventOutbox outbox =
                createPendingOutbox(
                        outboxId,
                        problemId,
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        when(
                outboxRepository.findAllByStatusOrderByOccurredAtAsc(
                        eq(ProblemEventOutboxStatus.PENDING),
                        any()
                )
        ).thenReturn(
                List.of(outbox)
        );

        CompletableFuture<SendResult<String, String>>
                completedFuture =
                CompletableFuture.completedFuture(
                        null
                );

        when(
                kafkaProducer.publish(
                        problemId,
                        outbox.getPayload()
                )
        ).thenReturn(
                completedFuture
        );

        // when
        relayService.relayPendingOutboxes();

        // then
        verify(kafkaProducer).publish(
                problemId,
                outbox.getPayload()
        );

        verify(statusService).markPublished(
                eq(outboxId),
                any(Instant.class)
        );

        verify(statusService, never()).recordFailure(
                any(),
                any()
        );
    }

    @Test
    @DisplayName(
            "Kafka 비동기 발행 실패 시 실패를 기록하고 다음 Outbox 처리를 계속한다"
    )
    void relayPendingOutboxes_recordsFailureAndContinues_whenKafkaPublishFails() {
        // given
        UUID failedOutboxId =
                UUID.randomUUID();

        UUID failedProblemId =
                UUID.randomUUID();

        ProblemEventOutbox failedOutbox =
                createPendingOutbox(
                        failedOutboxId,
                        failedProblemId,
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        UUID successOutboxId =
                UUID.randomUUID();

        UUID successProblemId =
                UUID.randomUUID();

        ProblemEventOutbox successOutbox =
                createPendingOutbox(
                        successOutboxId,
                        successProblemId,
                        Instant.parse(
                                "2026-09-08T00:00:01Z"
                        )
                );

        when(
                outboxRepository.findAllByStatusOrderByOccurredAtAsc(
                        eq(ProblemEventOutboxStatus.PENDING),
                        any()
                )
        ).thenReturn(
                List.of(
                        failedOutbox,
                        successOutbox
                )
        );

        CompletableFuture<SendResult<String, String>>
                failedFuture =
                CompletableFuture.failedFuture(
                        new IllegalStateException(
                                "broker unavailable"
                        )
                );

        CompletableFuture<SendResult<String, String>>
                successFuture =
                CompletableFuture.completedFuture(
                        null
                );

        when(
                kafkaProducer.publish(
                        failedProblemId,
                        failedOutbox.getPayload()
                )
        ).thenReturn(
                failedFuture
        );

        when(
                kafkaProducer.publish(
                        successProblemId,
                        successOutbox.getPayload()
                )
        ).thenReturn(
                successFuture
        );

        // when
        relayService.relayPendingOutboxes();

        // then
        verify(statusService).recordFailure(
                eq(failedOutboxId),
                anyString()
        );

        verify(statusService, never()).markPublished(
                eq(failedOutboxId),
                any()
        );

        verify(statusService).markPublished(
                eq(successOutboxId),
                any(Instant.class)
        );
    }

    @Test
    @DisplayName(
            "Kafka send가 동기적으로 실패해도 실패를 기록하고 다음 Outbox 처리를 계속한다"
    )
    void relayPendingOutboxes_recordsFailureAndContinues_whenKafkaSendThrowsSynchronously() {
        // given
        UUID failedOutboxId =
                UUID.randomUUID();

        UUID failedProblemId =
                UUID.randomUUID();

        ProblemEventOutbox failedOutbox =
                createPendingOutbox(
                        failedOutboxId,
                        failedProblemId,
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        UUID successOutboxId =
                UUID.randomUUID();

        UUID successProblemId =
                UUID.randomUUID();

        ProblemEventOutbox successOutbox =
                createPendingOutbox(
                        successOutboxId,
                        successProblemId,
                        Instant.parse(
                                "2026-09-08T00:00:01Z"
                        )
                );

        when(
                outboxRepository.findAllByStatusOrderByOccurredAtAsc(
                        eq(ProblemEventOutboxStatus.PENDING),
                        any()
                )
        ).thenReturn(
                List.of(
                        failedOutbox,
                        successOutbox
                )
        );

        when(
                kafkaProducer.publish(
                        failedProblemId,
                        failedOutbox.getPayload()
                )
        ).thenThrow(
                new IllegalStateException(
                        "producer unavailable"
                )
        );

        when(
                kafkaProducer.publish(
                        successProblemId,
                        successOutbox.getPayload()
                )
        ).thenReturn(
                CompletableFuture.completedFuture(
                        null
                )
        );

        // when
        relayService.relayPendingOutboxes();

        // then
        verify(statusService).recordFailure(
                eq(failedOutboxId),
                anyString()
        );

        verify(statusService).markPublished(
                eq(successOutboxId),
                any(Instant.class)
        );
    }

    @Test
    @DisplayName(
            "Kafka ACK가 제한 시간 내 완료되지 않으면 timeout 실패를 기록한다"
    )
    void relayPendingOutboxes_recordsFailure_whenKafkaPublishTimesOut() {
        // given
        ProblemEventOutboxRelayService shortTimeoutRelayService =
                new ProblemEventOutboxRelayService(
                        outboxRepository,
                        kafkaProducer,
                        statusService,
                        BATCH_SIZE,
                        1L
                );

        UUID outboxId =
                UUID.randomUUID();

        UUID problemId =
                UUID.randomUUID();

        ProblemEventOutbox outbox =
                createPendingOutbox(
                        outboxId,
                        problemId,
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        when(
                outboxRepository.findAllByStatusOrderByOccurredAtAsc(
                        eq(ProblemEventOutboxStatus.PENDING),
                        any()
                )
        ).thenReturn(
                List.of(outbox)
        );

        CompletableFuture<SendResult<String, String>>
                neverCompletes =
                new CompletableFuture<>();

        when(
                kafkaProducer.publish(
                        problemId,
                        outbox.getPayload()
                )
        ).thenReturn(
                neverCompletes
        );

        // when
        shortTimeoutRelayService.relayPendingOutboxes();

        // then
        verify(statusService).recordFailure(
                outboxId,
                "KAFKA_PUBLISH_TIMEOUT"
        );

        verify(statusService, never()).markPublished(
                eq(outboxId),
                any()
        );
    }

    @Test
    @DisplayName(
            "Kafka 발행 성공 후 상태 저장이 실패해도 Kafka 실패로 기록하지 않고 다음 Outbox를 처리한다"
    )
    void relayPendingOutboxes_continuesWithoutFailureRecord_whenPublishedStateUpdateFails() {
        // given
        UUID firstOutboxId =
                UUID.randomUUID();

        UUID firstProblemId =
                UUID.randomUUID();

        ProblemEventOutbox firstOutbox =
                createPendingOutbox(
                        firstOutboxId,
                        firstProblemId,
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                );

        UUID secondOutboxId =
                UUID.randomUUID();

        UUID secondProblemId =
                UUID.randomUUID();

        ProblemEventOutbox secondOutbox =
                createPendingOutbox(
                        secondOutboxId,
                        secondProblemId,
                        Instant.parse(
                                "2026-09-08T00:00:01Z"
                        )
                );

        when(
                outboxRepository.findAllByStatusOrderByOccurredAtAsc(
                        eq(ProblemEventOutboxStatus.PENDING),
                        any()
                )
        ).thenReturn(
                List.of(
                        firstOutbox,
                        secondOutbox
                )
        );

        when(
                kafkaProducer.publish(
                        firstProblemId,
                        firstOutbox.getPayload()
                )
        ).thenReturn(
                CompletableFuture.completedFuture(
                        null
                )
        );

        when(
                kafkaProducer.publish(
                        secondProblemId,
                        secondOutbox.getPayload()
                )
        ).thenReturn(
                CompletableFuture.completedFuture(
                        null
                )
        );

        doThrow(
                new IllegalStateException(
                        "database unavailable"
                )
        ).when(
                statusService
        ).markPublished(
                eq(firstOutboxId),
                any(Instant.class)
        );

        // when
        relayService.relayPendingOutboxes();

        // then
        verify(statusService, never()).recordFailure(
                eq(firstOutboxId),
                anyString()
        );

        verify(statusService).markPublished(
                eq(secondOutboxId),
                any(Instant.class)
        );
    }

    private ProblemEventOutbox createPendingOutbox(
            UUID outboxId,
            UUID problemId,
            Instant occurredAt
    ) {
        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        UUID.randomUUID(),
                        problemId,
                        1,
                        """
                        {
                          "eventType": "PROBLEM_PUBLISHED"
                        }
                        """,
                        occurredAt
                );

        ReflectionTestUtils.setField(
                outbox,
                "id",
                outboxId
        );

        return outbox;
    }
}
