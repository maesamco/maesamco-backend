package com.maesamco.content.application.persistence_service;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Problem Event Outbox의 상태 변경을 짧은 DB 트랜잭션으로 처리합니다.
 * Kafka 발행 자체는 Facade가 담당하고, 실제 DB 쓰기만 이 서비스에서 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemEventOutboxPersistenceService {

    private static final int MAX_RELAY_ATTEMPTS = 5;

    private final ProblemEventOutboxRepository problemEventOutboxRepository;

    // Kafka 발행 성공 후 Outbox를 PUBLISHED 상태로 변경합니다.
    @Transactional
    public void markPublished(UUID outboxId) {
        ProblemEventOutbox outbox = getOutbox(outboxId);

        if (outbox.getStatus() != ProblemEventOutboxStatus.PENDING) {
            return;
        }

        outbox.markPublished(Instant.now());
        problemEventOutboxRepository.save(outbox);
    }

    // Kafka 발행 실패를 기록하고 재시도 상한에 도달하면 FAILED 상태로 종료합니다.
    @Transactional
    public void recordFailedAttempt(UUID outboxId, String error) {
        ProblemEventOutbox outbox = getOutbox(outboxId);

        if (outbox.getStatus() != ProblemEventOutboxStatus.PENDING) {
            return;
        }

        outbox.recordFailure(error, MAX_RELAY_ATTEMPTS);
        problemEventOutboxRepository.save(outbox);

        if (outbox.getStatus() == ProblemEventOutboxStatus.FAILED) {
            log.error("[Content] Problem Event Outbox 재시도 상한({}) 도달 — FAILED 처리. outboxId={}, eventType={}",
                    MAX_RELAY_ATTEMPTS, outbox.getId(), outbox.getEventType());
        }
    }

    // Kafka 발행은 성공했지만 Outbox 완료 상태 저장에 실패한 경우 별도로 실패를 기록합니다.
    @Transactional
    public void recordPostPublishFailure(UUID outboxId, String error) {
        ProblemEventOutbox outbox = getOutbox(outboxId);

        if (outbox.getStatus() != ProblemEventOutboxStatus.PENDING) {
            return;
        }

        outbox.recordFailure(error, MAX_RELAY_ATTEMPTS);
        problemEventOutboxRepository.save(outbox);

        if (outbox.getStatus() == ProblemEventOutboxStatus.FAILED) {
            log.error("[Content] Kafka 발행 후 Outbox 상태 갱신이 반복 실패하여 FAILED 처리. outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType());
        }
    }

    // Relay가 지원하지 않는 eventType은 재시도해도 복구되지 않으므로 즉시 FAILED 처리합니다.
    @Transactional
    public void markUnsupportedEventType(UUID outboxId, String error) {
        ProblemEventOutbox outbox = getOutbox(outboxId);

        if (outbox.getStatus() != ProblemEventOutboxStatus.PENDING) {
            return;
        }

        outbox.markFailed(error);
        problemEventOutboxRepository.save(outbox);
    }

    private ProblemEventOutbox getOutbox(UUID outboxId) {
        return problemEventOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "Problem Event Outbox를 찾을 수 없습니다. outboxId=" + outboxId
                ));
    }
}