package com.maesamco.content.application.persistence_service;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox Relay가 Kafka 호출 사이에서 사용하는 짧은 DB 트랜잭션을 처리합니다.
 * Kafka 발행은 Facade가 담당하고, 이 서비스는 Outbox 선점과 상태 변경만 담당합니다.
 *
 * <p>발행 결과 기록 메서드는 모두 claimId를 받아, 현재 선점을 보유한 Worker인지 확인합니다.
 * lease 만료로 다른 Worker가 재선점한 뒤 늦게 도착한 결과는 무시합니다(fencing).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemEventOutboxPersistenceService {

    private static final int MAX_RELAY_ATTEMPTS = 5;

    private final ProblemEventOutboxRepository problemEventOutboxRepository;

    /**
     * 발행 가능한 Outbox 한 건을 선점합니다.
     *
     * <p>FOR UPDATE SKIP LOCKED 로 조회하므로 여러 인스턴스가 동시에 호출해도
     * 같은 행을 선점하지 않습니다. 선점 결과(IN_PROGRESS, claimId, leaseUntil)는
     * 이 트랜잭션이 커밋되는 즉시 다른 인스턴스에 보이며, 행 잠금은 커밋과 함께 해제됩니다.
     * 따라서 Kafka ACK를 기다리는 동안 DB 잠금이나 트랜잭션을 유지하지 않습니다.</p>
     *
     * @param claimId 이번 발행 시도를 식별하는 선점 ID
     * @param leaseDuration 선점 유지 시간
     * @return 선점한 Outbox. 선점할 대상이 없으면 empty
     */
    @Transactional
    public Optional<ProblemEventOutbox> claimNext(UUID claimId, Duration leaseDuration) {
        Instant now = Instant.now();

        List<ProblemEventOutbox> candidates =
                problemEventOutboxRepository.findClaimableForUpdate(now, 1);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        ProblemEventOutbox outbox = candidates.get(0);
        boolean reclaimingExpiredLease = outbox.getStatus() == ProblemEventOutboxStatus.IN_PROGRESS;

        outbox.claim(claimId, now, now.plus(leaseDuration));
        problemEventOutboxRepository.save(outbox);

        if (reclaimingExpiredLease) {
            log.warn(
                    "[Content] lease가 만료된 IN_PROGRESS Outbox를 재선점합니다. "
                            + "이전 Worker가 종료되었거나 응답하지 않은 것으로 간주합니다. outboxId={}, eventId={}",
                    outbox.getId(),
                    outbox.getEventId()
            );
        }

        return Optional.of(outbox);
    }

    // Kafka 발행 성공 후 Outbox를 PUBLISHED 상태로 변경합니다.
    @Transactional
    public boolean markPublished(UUID outboxId, UUID claimId) {
        ProblemEventOutbox freshOutbox = getOutbox(outboxId);

        if (!hasActiveClaim(freshOutbox, claimId, "PUBLISHED 처리")) {
            return false;
        }

        freshOutbox.markPublished(claimId, Instant.now());
        problemEventOutboxRepository.save(freshOutbox);
        return true;
    }

    // Kafka 발행 자체가 실패한 경우 재시도 횟수를 증가시키고 상한 도달 시 FAILED 처리합니다.
    @Transactional
    public boolean recordFailedAttempt(UUID outboxId, UUID claimId, String error) {
        ProblemEventOutbox freshOutbox = getOutbox(outboxId);

        if (!hasActiveClaim(freshOutbox, claimId, "발행 실패 기록")) {
            return false;
        }

        freshOutbox.recordFailure(claimId, error, MAX_RELAY_ATTEMPTS);
        problemEventOutboxRepository.save(freshOutbox);

        if (freshOutbox.getStatus() == ProblemEventOutboxStatus.FAILED) {
            log.error(
                    "[Content] Problem Event Outbox 재시도 상한({}) 도달 — FAILED 처리. outboxId={}, eventType={}",
                    MAX_RELAY_ATTEMPTS,
                    freshOutbox.getId(),
                    freshOutbox.getEventType()
            );
        }
        return true;
    }

    // Kafka에 이미 전달됐을 가능성이 있는 실패는 FAILED로 종료하지 않고 PENDING 상태로 되돌립니다.
    @Transactional
    public boolean recordPostPublishFailure(UUID outboxId, UUID claimId, String error) {
        ProblemEventOutbox freshOutbox = getOutbox(outboxId);

        if (!hasActiveClaim(freshOutbox, claimId, "발행 결과 미확정 기록")) {
            return false;
        }

        freshOutbox.recordPostPublishFailure(claimId, error);
        problemEventOutboxRepository.save(freshOutbox);

        log.error(
                "[Content] Kafka 발행 결과를 확정할 수 없어 PENDING 상태로 되돌립니다. "
                        + "retryCount={}, outboxId={}, eventType={}",
                freshOutbox.getRetryCount(),
                freshOutbox.getId(),
                freshOutbox.getEventType()
        );
        return true;
    }

    // 재시도로 복구할 수 없는 실패는 즉시 FAILED 처리합니다.
    @Transactional
    public boolean markFailed(UUID outboxId, UUID claimId, String error) {
        ProblemEventOutbox freshOutbox = getOutbox(outboxId);

        if (!hasActiveClaim(freshOutbox, claimId, "FAILED 처리")) {
            return false;
        }

        freshOutbox.markFailed(claimId, error);
        problemEventOutboxRepository.save(freshOutbox);
        return true;
    }

    /**
     * 현재 선점을 보유하고 있는지 확인합니다.
     * lease 만료 후 다른 Worker가 재선점했거나 이미 처리된 경우 상태를 변경하지 않습니다.
     */
    private boolean hasActiveClaim(ProblemEventOutbox outbox, UUID claimId, String action) {
        if (outbox.isClaimedBy(claimId)) {
            return true;
        }

        log.warn(
                "[Content] Outbox {} 생략 — 현재 선점을 보유하지 않은 Worker입니다. "
                        + "lease 만료 후 다른 Worker가 재선점했을 수 있습니다. outboxId={}, status={}",
                action,
                outbox.getId(),
                outbox.getStatus()
        );
        return false;
    }

    private ProblemEventOutbox getOutbox(UUID outboxId) {
        return problemEventOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "Problem Event Outbox를 찾을 수 없습니다. outboxId=" + outboxId
                ));
    }
}
