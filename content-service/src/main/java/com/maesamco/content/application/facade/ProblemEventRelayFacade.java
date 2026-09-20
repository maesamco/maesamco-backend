package com.maesamco.content.application.facade;

import com.maesamco.content.application.persistence_service.ProblemEventOutboxPersistenceService;
import com.maesamco.content.application.port.EventPublishOutcomeUnknownException;
import com.maesamco.content.application.port.EventPublisherPort;
import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * PENDING 상태의 Problem Event Outbox를 조회해 Kafka로 발행합니다.
 * Kafka 외부 호출과 DB 상태 변경을 조율하며 Facade 자체에는 트랜잭션을 두지 않습니다.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.problem-published.relay", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ProblemEventRelayFacade {

    private static final String PAYLOAD_TOO_LARGE_ERROR = "EVENT_PAYLOAD_TOO_LARGE";
    private static final String PUBLISH_OUTCOME_UNKNOWN_ERROR = "KAFKA_PUBLISH_OUTCOME_UNKNOWN";
    private static final String POST_PUBLISH_FAILURE_ERROR = "OUTBOX_POST_PUBLISH_FAILURE";

    private final ProblemEventOutboxRepository problemEventOutboxRepository;
    private final ProblemEventOutboxPersistenceService problemEventOutboxPersistenceService;
    private final EventPublisherPort eventPublisherPort;

    @Value("${spring.kafka.topic.problem-published}")
    private String problemPublishedTopic;

    @Value("${outbox.problem-published.relay.batch-size:50}")
    private int batchSize;

    @Value("${outbox.problem-published.relay.max-payload-bytes:900000}")
    private int maxPayloadBytes;

    @Scheduled(fixedDelayString = "${outbox.problem-published.relay.fixed-delay-ms:1000}")
    public void relay() {
        List<ProblemEventOutbox> pendingOutboxes =
                problemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                        ProblemEventOutboxStatus.PENDING,
                        batchSize
                );

        for (ProblemEventOutbox outbox : pendingOutboxes) {
            try {
                relayOne(outbox);
            } catch (Exception e) {
                log.error("[Content] Outbox 처리 중 예상치 못한 예외 — 해당 Outbox만 건너뜁니다. outboxId={}",
                        outbox.getId(), e);
            }

            if (Thread.currentThread().isInterrupted()) {
                log.warn("[Content] 인터럽트 감지 — 남은 Outbox 배치 처리를 중단합니다.");
                break;
            }
        }
    }

    private void relayOne(ProblemEventOutbox outbox) {
        if (isPayloadTooLarge(outbox.getPayload())) {
            problemEventOutboxPersistenceService.markFailed(outbox.getId(), PAYLOAD_TOO_LARGE_ERROR);

            log.error("[Content] ProblemPublished payload 크기 초과 — FAILED 처리. outboxId={}, eventId={}",
                    outbox.getId(), outbox.getEventId());
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
                    PUBLISH_OUTCOME_UNKNOWN_ERROR
            );

            log.error("[Content] Outbox 발행 결과를 확인할 수 없음 — PENDING 상태로 유지합니다. outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType(), e);
            return;
        } catch (Exception e) {
            problemEventOutboxPersistenceService.recordFailedAttempt(
                    outbox.getId(),
                    safePublishError(e)
            );

            log.error("[Content] Outbox 발행 실패 — 재시도 상한 전이면 다음 폴링에서 재시도합니다. outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType(), e);
            return;
        }

        try {
            problemEventOutboxPersistenceService.markPublished(outbox.getId());

            log.info("[Content] Outbox 발행 성공. outboxId={}, eventType={}, aggregateId={}",
                    outbox.getId(), outbox.getEventType(), outbox.getAggregateId());
        } catch (Exception e) {
            problemEventOutboxPersistenceService.recordPostPublishFailure(
                    outbox.getId(),
                    POST_PUBLISH_FAILURE_ERROR
            );

            log.error("[Content] Kafka 발행은 성공했으나 Outbox 완료 상태 저장 실패 — PENDING 상태로 유지합니다. outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType(), e);
        }
    }

    private boolean isPayloadTooLarge(String payload) {
        return payload.getBytes(StandardCharsets.UTF_8).length > maxPayloadBytes;
    }

    private String safePublishError(Throwable throwable) {
        return "KAFKA_PUBLISH_FAILED:" + throwable.getClass().getSimpleName();
    }
}