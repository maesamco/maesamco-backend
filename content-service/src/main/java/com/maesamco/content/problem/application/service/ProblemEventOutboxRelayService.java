package com.maesamco.content.problem.application.service;

import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import com.maesamco.content.problem.infrastructure.messaging.producer.ProblemPublishedKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PENDING 상태의 ProblemPublished Outbox 이벤트를 Kafka로 전달합니다.
 *
 * <p>Kafka 전송 중에는 DB 트랜잭션을 유지하지 않습니다.
 * Kafka ACK가 확인된 이후에만 별도의 트랜잭션 서비스인
 * {@link ProblemEventOutboxStatusService}를 통해 Outbox 상태를 변경합니다.</p>
 *
 * <p>Kafka 발행 성공 후 Outbox 상태 변경 전에 장애가 발생할 경우
 * 동일 이벤트가 다시 발행될 수 있으므로 전달 보장은 At-least-once입니다.</p>
 */
@Slf4j
@Service
public class ProblemEventOutboxRelayService {

    private static final String PAYLOAD_TOO_LARGE_ERROR =
            "EVENT_PAYLOAD_TOO_LARGE";

    private final ProblemEventOutboxRepository outboxRepository;
    private final ProblemPublishedKafkaProducer kafkaProducer;
    private final ProblemEventOutboxStatusService statusService;

    private final int batchSize;
    private final long publishTimeoutMillis;
    private final int maxPayloadBytes;

    public ProblemEventOutboxRelayService(
            ProblemEventOutboxRepository outboxRepository,
            ProblemPublishedKafkaProducer kafkaProducer,
            ProblemEventOutboxStatusService statusService,
            @Value(
                    "${outbox.problem-published.relay.batch-size:50}"
            )
            int batchSize,
            @Value(
                    "${outbox.problem-published.relay.publish-timeout-ms:5000}"
            )
            long publishTimeoutMillis,
            @Value(
                    "${outbox.problem-published.relay.max-payload-bytes:900000}"
            )
            int maxPayloadBytes
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaProducer = kafkaProducer;
        this.statusService = statusService;
        this.batchSize = batchSize;
        this.publishTimeoutMillis = publishTimeoutMillis;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    /**
     * 오래된 PENDING Outbox부터 한 배치씩 Kafka로 발행합니다.
     */
    public void relayPendingOutboxes() {
        List<ProblemEventOutbox> pendingOutboxes =
                outboxRepository
                        .findAllByStatusOrderByOccurredAtAsc(
                                ProblemEventOutboxStatus.PENDING,
                                PageRequest.of(
                                        0,
                                        batchSize
                                )
                        );

        for (ProblemEventOutbox outbox : pendingOutboxes) {
            boolean continueRelay =
                    relayOne(outbox);

            if (!continueRelay) {
                return;
            }
        }
    }

    /**
     * Outbox 하나를 Kafka로 발행합니다.
     *
     * @return 다음 Outbox 처리를 계속할 수 있으면 true,
     *         현재 스레드가 interrupt되었으면 false
     */
    private boolean relayOne(
            ProblemEventOutbox outbox
    ) {
        if (isPayloadTooLarge(
                outbox.getPayload()
        )) {
            recordFailureSafely(
                    outbox,
                    PAYLOAD_TOO_LARGE_ERROR
            );

            return true;
        }

        try {
            CompletableFuture<SendResult<String, String>>
                    publishFuture =
                    kafkaProducer.publish(
                            outbox.getAggregateId(),
                            outbox.getPayload()
                    );

            publishFuture.get(
                    publishTimeoutMillis,
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            recordFailureSafely(
                    outbox,
                    "KAFKA_PUBLISH_INTERRUPTED"
            );

            return false;
        } catch (TimeoutException exception) {
            recordFailureSafely(
                    outbox,
                    "KAFKA_PUBLISH_TIMEOUT"
            );

            return true;
        } catch (ExecutionException exception) {
            recordFailureSafely(
                    outbox,
                    safePublishError(
                            exception.getCause()
                    )
            );

            return true;
        } catch (RuntimeException exception) {
            recordFailureSafely(
                    outbox,
                    safePublishError(
                            exception
                    )
            );

            return true;
        }

        markPublishedSafely(
                outbox
        );

        return true;
    }

    /**
     * Kafka 메시지 제한보다 충분한 여유를 두기 위해
     * 직렬화된 JSON payload를 UTF-8 byte 기준으로 검사합니다.
     */
    private boolean isPayloadTooLarge(
            String payload
    ) {
        return payload.getBytes(
                StandardCharsets.UTF_8
        ).length > maxPayloadBytes;
    }

    /**
     * Kafka ACK 이후 Outbox를 PUBLISHED로 변경합니다.
     *
     * <p>Kafka는 이미 성공한 상태이므로 상태 저장 실패를
     * Kafka 발행 실패로 다시 기록하지 않습니다.
     * DB 변경에 실패하면 Outbox는 PENDING으로 남고
     * 이후 다시 발행될 수 있습니다.</p>
     */
    private void markPublishedSafely(
            ProblemEventOutbox outbox
    ) {
        try {
            statusService.markPublished(
                    outbox.getId(),
                    Instant.now()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "ProblemPublished Outbox 상태 갱신 실패. "
                            + "outboxId={}, eventId={}, problemId={}, errorType={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * Kafka 발행 실패를 별도 트랜잭션으로 기록합니다.
     */
    private void recordFailureSafely(
            ProblemEventOutbox outbox,
            String safeError
    ) {
        try {
            statusService.recordFailure(
                    outbox.getId(),
                    safeError
            );
        } catch (RuntimeException exception) {
            log.error(
                    "ProblemPublished Outbox 실패 상태 저장 실패. "
                            + "outboxId={}, eventId={}, problemId={}, errorType={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * Kafka 예외 메시지에는 payload가 포함될 가능성을 완전히
     * 배제할 수 없으므로 예외 메시지는 저장하지 않고 타입만 기록합니다.
     */
    private String safePublishError(
            Throwable throwable
    ) {
        if (throwable == null) {
            return "KAFKA_PUBLISH_FAILED";
        }

        return "KAFKA_PUBLISH_FAILED:"
                + throwable.getClass().getSimpleName();
    }
}
