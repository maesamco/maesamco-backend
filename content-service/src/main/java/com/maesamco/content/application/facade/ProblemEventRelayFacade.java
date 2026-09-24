package com.maesamco.content.application.facade;

import com.maesamco.content.application.persistence_service.ProblemEventOutboxPersistenceService;
import com.maesamco.content.application.port.EventPublishOutcomeUnknownException;
import com.maesamco.content.application.port.EventPublisherPort;
import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 발행 가능한 Problem Event Outbox를 한 건씩 선점해 Kafka로 발행합니다.
 * Kafka 외부 호출과 DB 상태 변경을 조율하며 Facade 자체에는 트랜잭션을 두지 않습니다.
 *
 * <h3>다중 인스턴스 동작 (#160)</h3>
 * <ol>
 *     <li>짧은 트랜잭션에서 FOR UPDATE SKIP LOCKED 로 한 건을 조회하고
 *     IN_PROGRESS + claimId + leaseUntil 을 기록한 뒤 커밋합니다. (선점)</li>
 *     <li>트랜잭션 밖에서 Kafka로 발행하고 ACK를 기다립니다.</li>
 *     <li>짧은 트랜잭션에서 claimId가 일치할 때만 결과(PUBLISHED / PENDING 재시도 / FAILED)를 기록합니다.</li>
 * </ol>
 *
 * <p>배치 전체가 아니라 한 건씩 선점하므로 lease는 "한 건 발행 시간"만 커버하면 됩니다.
 * 처리 중 인스턴스가 종료되면 lease 만료 후 다른 인스턴스가 재선점하므로 이벤트는 유실되지 않습니다.
 * 전달 보장은 기존과 동일하게 At-least-once 이며, Consumer는 eventId 기반 멱등 처리를 유지해야 합니다.</p>
 */
@Component
@ConditionalOnProperty(prefix = "outbox.problem-published.relay", name = "enabled", havingValue = "true")
@Slf4j
public class ProblemEventRelayFacade {

    private static final long MIN_LEASE_SAFETY_MARGIN_MILLIS = 1_000L;

    private static final String PAYLOAD_TOO_LARGE_ERROR = "EVENT_PAYLOAD_TOO_LARGE";
    private static final String PUBLISH_OUTCOME_UNKNOWN_ERROR = "KAFKA_PUBLISH_OUTCOME_UNKNOWN";
    private static final String POST_PUBLISH_FAILURE_ERROR = "OUTBOX_POST_PUBLISH_FAILURE";

    private final ProblemEventOutboxPersistenceService problemEventOutboxPersistenceService;
    private final EventPublisherPort eventPublisherPort;
    private final String problemPublishedTopic;
    private final int batchSize;
    private final int maxPayloadBytes;
    private final Duration leaseDuration;

    public ProblemEventRelayFacade(
            ProblemEventOutboxPersistenceService problemEventOutboxPersistenceService,
            EventPublisherPort eventPublisherPort,
            @Value("${spring.kafka.topic.problem-published}")
            String problemPublishedTopic,
            @Value("${outbox.problem-published.relay.batch-size:50}")
            int batchSize,
            @Value("${outbox.problem-published.relay.max-payload-bytes:900000}")
            int maxPayloadBytes,
            @Value("${outbox.problem-published.relay.lease-duration-ms:60000}")
            long leaseDurationMillis,
            @Value("${outbox.problem-published.relay.publish-timeout-ms:5000}")
            long publishTimeoutMillis
    ) {
        validateSettings(batchSize, maxPayloadBytes, leaseDurationMillis, publishTimeoutMillis);

        this.problemEventOutboxPersistenceService = problemEventOutboxPersistenceService;
        this.eventPublisherPort = eventPublisherPort;
        this.problemPublishedTopic = problemPublishedTopic;
        this.batchSize = batchSize;
        this.maxPayloadBytes = maxPayloadBytes;
        this.leaseDuration = Duration.ofMillis(leaseDurationMillis);
    }

    @Scheduled(fixedDelayString = "${outbox.problem-published.relay.fixed-delay-ms:1000}")
    public void relay() {
        for (int processed = 0; processed < batchSize; processed++) {
            Optional<ProblemEventOutbox> claimed = claimNextSafely();

            if (claimed.isEmpty()) {
                return;
            }

            ProblemEventOutbox outbox = claimed.get();

            try {
                relayOne(outbox);
            } catch (Exception e) {
                // 결과 기록에 실패해도 선점은 lease 만료 후 자동으로 풀리므로 이벤트가 유실되지 않습니다.
                log.error(
                        "[Content] Outbox 처리 중 예상치 못한 예외 — lease 만료 후 재시도됩니다. outboxId={}",
                        outbox.getId(),
                        e
                );
            }

            if (Thread.currentThread().isInterrupted()) {
                log.warn("[Content] 인터럽트 감지 — 남은 Outbox 배치 처리를 중단합니다.");
                return;
            }
        }
    }

    private Optional<ProblemEventOutbox> claimNextSafely() {
        try {
            return problemEventOutboxPersistenceService.claimNext(UUID.randomUUID(), leaseDuration);
        } catch (Exception e) {
            log.error("[Content] Outbox 선점 중 예외 — 이번 폴링을 중단합니다.", e);
            return Optional.empty();
        }
    }

    private void relayOne(ProblemEventOutbox outbox) {
        UUID claimId = outbox.getClaimId();

        if (isPayloadTooLarge(outbox.getPayload())) {
            problemEventOutboxPersistenceService.markFailed(
                    outbox.getId(),
                    claimId,
                    PAYLOAD_TOO_LARGE_ERROR
            );

            log.error(
                    "[Content] ProblemPublished payload 크기 초과 — FAILED 처리. outboxId={}, eventId={}",
                    outbox.getId(),
                    outbox.getEventId()
            );
            return;
        }

        try {
            eventPublisherPort.publish(
                    problemPublishedTopic,
                    outbox.getAggregateId().toString(),
                    outbox.getPayload()
            );
        } catch (EventPublishOutcomeUnknownException e) {
            problemEventOutboxPersistenceService.recordPostPublishFailure(
                    outbox.getId(),
                    claimId,
                    PUBLISH_OUTCOME_UNKNOWN_ERROR
            );

            log.error(
                    "[Content] Outbox 발행 결과를 확인할 수 없음 — PENDING 상태로 되돌립니다. outboxId={}, eventType={}",
                    outbox.getId(),
                    outbox.getEventType(),
                    e
            );
            return;
        } catch (Exception e) {
            problemEventOutboxPersistenceService.recordFailedAttempt(
                    outbox.getId(),
                    claimId,
                    safePublishError(e)
            );

            log.error(
                    "[Content] Outbox 발행 실패 — 재시도 상한 전이면 다음 폴링에서 재시도합니다. outboxId={}, eventType={}",
                    outbox.getId(),
                    outbox.getEventType(),
                    e
            );
            return;
        }

        try {
            boolean updated = problemEventOutboxPersistenceService.markPublished(outbox.getId(), claimId);

            if (updated) {
                log.info(
                        "[Content] Outbox 발행 성공. outboxId={}, eventType={}, aggregateId={}",
                        outbox.getId(),
                        outbox.getEventType(),
                        outbox.getAggregateId()
                );
            }
        } catch (Exception e) {
            problemEventOutboxPersistenceService.recordPostPublishFailure(
                    outbox.getId(),
                    claimId,
                    POST_PUBLISH_FAILURE_ERROR
            );

            log.error(
                    "[Content] Kafka 발행은 성공했으나 Outbox 완료 상태 저장 실패 — PENDING 상태로 되돌립니다. outboxId={}, eventType={}",
                    outbox.getId(),
                    outbox.getEventType(),
                    e
            );
        }
    }

    private boolean isPayloadTooLarge(String payload) {
        return payload.getBytes(StandardCharsets.UTF_8).length > maxPayloadBytes;
    }

    private String safePublishError(Throwable throwable) {
        return "KAFKA_PUBLISH_FAILED:" + throwable.getClass().getSimpleName();
    }

    /**
     * lease가 Kafka ACK 대기 시간보다 짧으면, 정상 처리 중인 행을 다른 인스턴스가
     * 재선점해 중복 발행할 수 있으므로 기동 시점에 차단합니다.
     */
    private static void validateSettings(
            int batchSize,
            int maxPayloadBytes,
            long leaseDurationMillis,
            long publishTimeoutMillis
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Outbox Relay 배치 크기는 1 이상이어야 합니다.");
        }
        if (maxPayloadBytes < 1) {
            throw new IllegalArgumentException("Outbox payload 최대 크기는 1바이트 이상이어야 합니다.");
        }
        if (publishTimeoutMillis < 1) {
            throw new IllegalArgumentException("Kafka ACK 대기 시간은 1ms 이상이어야 합니다.");
        }
        if (leaseDurationMillis - publishTimeoutMillis < MIN_LEASE_SAFETY_MARGIN_MILLIS) {
            throw new IllegalArgumentException(
                    "Outbox 선점 시간(lease-duration-ms)은 Kafka ACK 대기 시간(publish-timeout-ms)보다 "
                            + "최소 1000ms 길어야 합니다."
            );
        }
    }
}
