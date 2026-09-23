package com.maesamco.content.application.persistence_service;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox Relay가 Kafka 호출 사이에서 사용하는 짧은 DB 트랜잭션을 처리합니다.
 * Kafka 발행은 Facade가 담당하고, 이 서비스는 Outbox 상태 변경만 담당합니다.
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
        ProblemEventOutbox freshOutbox = getOutbox(outboxId);

        if (freshOutbox.getStatus() != ProblemEventOutboxStatus.PENDING) {
            return;
        }

        freshOutbox.markPublished(Instant.now());

        try {
            problemEventOutboxRepository.save(freshOutbox);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info(
                    "[Content] Outbox PUBLISHED 처리 중 낙관적 락 충돌 — 다른 Relay가 먼저 처리함. outboxId={}",
                    outboxId
            );
        }
    }

    // Kafka 발행 자체가 실패한 경우 재시도 횟수를 증가시키고 상한 도달 시 FAILED 처리합니다.
    @Transactional
    public void recordFailedAttempt(UUID outboxId, String error) {
        ProblemEventOutbox freshOutbox = getOutbox(outboxId);

        if (freshOutbox.getStatus() != ProblemEventOutboxStatus.PENDING) {
            return;
        }

        freshOutbox.recordFailure(error, MAX_RELAY_ATTEMPTS);

        try {
            problemEventOutboxRepository.save(freshOutbox);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info(
                    "[Content] Outbox 발행 실패 기록 중 낙관적 락 충돌 — 다른 Relay가 먼저 처리함. outboxId={}",
                    outboxId
            );
            return;
        }

        if (freshOutbox.getStatus() == ProblemEventOutboxStatus.FAILED) {
            log.error(
                    "[Content] Problem Event Outbox 재시도 상한({}) 도달 — FAILED 처리. outboxId={}, eventType={}",
                    MAX_RELAY_ATTEMPTS,
                    freshOutbox.getId(),
                    freshOutbox.getEventType()
            );
        }
    }

    // Kafka에 이미 전달됐을 가능성이 있는 실패는 FAILED로 종료하지 않고 PENDING 상태를 유지합니다.
    @Transactional
    public void recordPostPublishFailure(UUID outboxId, String error) {
        ProblemEventOutbox freshOutbox = getOutbox(outboxId);

        if (freshOutbox.getStatus() != ProblemEventOutboxStatus.PENDING) {
            return;
        }

        freshOutbox.recordPostPublishFailure(error);

        try {
            problemEventOutboxRepository.save(freshOutbox);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info(
                    "[Content] Outbox 발행 결과 미확정 기록 중 낙관적 락 충돌 — 다른 Relay가 먼저 처리함. outboxId={}",
                    outboxId
            );
            return;
        }

        log.error(
                "[Content] Kafka 발행 결과를 확정할 수 없어 PENDING 상태로 유지합니다. "
                        + "retryCount={}, outboxId={}, eventType={}",
                freshOutbox.getRetryCount(),
                freshOutbox.getId(),
                freshOutbox.getEventType()
        );
    }

    // 재시도로 복구할 수 없는 실패는 즉시 FAILED 처리합니다.
    @Transactional
    public void markFailed(UUID outboxId, String error) {
        ProblemEventOutbox freshOutbox = getOutbox(outboxId);

        if (freshOutbox.getStatus() != ProblemEventOutboxStatus.PENDING) {
            return;
        }

        freshOutbox.markFailed(error);

        try {
            problemEventOutboxRepository.save(freshOutbox);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info(
                    "[Content] Outbox FAILED 처리 중 낙관적 락 충돌 — 다른 Relay가 먼저 처리함. outboxId={}",
                    outboxId
            );
        }
    }

    private ProblemEventOutbox getOutbox(UUID outboxId) {
        return problemEventOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "Problem Event Outbox를 찾을 수 없습니다. outboxId=" + outboxId
                ));
    }
}