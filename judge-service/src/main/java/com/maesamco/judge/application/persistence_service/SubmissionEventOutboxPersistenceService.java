package com.maesamco.judge.application.persistence_service;

import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

/**
 * Outbox Relay(Facade)가 Kafka 호출 사이사이에 배치하는 짧은 DB 트랜잭션 조각
 * (팀 컨벤션 2절 Facade — persistence_service). Facade엔 @Transactional을 안 붙이고
 * 실제 DB 쓰기만 여기서 별도 트랜잭션으로 커밋합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionEventOutboxPersistenceService {

    private static final int MAX_RELAY_ATTEMPTS = 5;
    private static final int MAX_POST_PUBLISH_FAILURE_ATTEMPTS = 5;

    private final SubmissionEventOutboxRepository submissionEventOutboxRepository;
    private final SubmissionRepository submissionRepository;

    /**
     * 발행 가능한 Outbox 한 건을 선점한다(#272). FOR UPDATE SKIP LOCKED 로 조회하므로 여러 인스턴스가 동시에
     * 호출해도 같은 행을 선점하지 않는다. 선점 결과(IN_PROGRESS, claimId, leaseUntil)는 이 트랜잭션이 커밋되는
     * 즉시 다른 인스턴스에 보이고 행 잠금은 커밋과 함께 풀리므로, Kafka 응답을 기다리는 동안 DB 잠금·트랜잭션을 쥐지 않는다.
     */
    @Transactional
    public Optional<SubmissionEventOutbox> claimNext(UUID claimId, Duration leaseDuration) {
        Instant now = Instant.now();
        List<SubmissionEventOutbox> candidates = submissionEventOutboxRepository.findClaimableForUpdate(
                OutboxStatus.PENDING, OutboxStatus.IN_PROGRESS, now, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        SubmissionEventOutbox outbox = candidates.get(0);
        boolean reclaimingExpiredLease = outbox.getStatus() == OutboxStatus.IN_PROGRESS;
        outbox.claimForPublish(claimId, now, now.plus(leaseDuration));
        submissionEventOutboxRepository.saveAndFlush(outbox);

        if (reclaimingExpiredLease) {
            log.warn("[Judge] lease가 만료된 IN_PROGRESS Outbox를 재선점합니다. 이전 Worker가 종료되었거나 응답하지 않은 것으로 "
                    + "간주합니다. outboxId={}, eventType={}", outbox.getId(), outbox.getEventType());
        }
        return Optional.of(outbox);
    }

    // 아래 결과 기록 메서드는 모두 (outboxId, claimId)를 받아 id로 다시 조회한 뒤 "지금 이 claimId로 선점 중인지"를
    // 확인한다 — 오래된 in-memory 객체를 그대로 저장하면 이미 다른 Worker가 반영한 최신 상태를 덮어쓰고, lease가
    // 만료돼 다른 Worker가 재선점한 뒤 늦게 도착한 결과도 이 확인으로 걸러진다(fencing).
    // 낙관적 락 예외(@Version)는 여기서 잡지 않고 그대로 던진다 — 안쪽 save()가 트랜잭션을 rollback-only로 만들어서
    // catch로 정상 반환해도 커밋 시점에 UnexpectedRollbackException이 나기 때문이다(coaching #288). Facade가 잡는다.

    // Kafka 발행 성공 후 호출 — Outbox를 COMPLETED로 표시하는 것과 Submission을
    // PENDING -> QUEUED로 전이시키는 것을 같은 트랜잭션으로 묶음.
    @Transactional
    public boolean markPublished(UUID outboxId, UUID claimId) {
        SubmissionEventOutbox outbox = findActiveClaim(outboxId, claimId, "PUBLISHED 처리");
        if (outbox == null) {
            return false;
        }

        outbox.incrementAttemptCount();
        outbox.markPublished();
        submissionEventOutboxRepository.save(outbox);

        // JudgeRequested 발행 성공 시에만 PENDING -> QUEUED 전이.
        if ("JudgeRequested".equals(outbox.getEventType())) {
            markSubmissionQueued(outbox);
        }
        return true;
    }

    // Kafka 발행 자체가 실패했을 때 호출 — 상한 안이면 선점을 풀어 PENDING으로 되돌려 다음 폴링에서 재시도.
    @Transactional
    public void recordFailedAttempt(UUID outboxId, UUID claimId) {
        SubmissionEventOutbox outbox = findActiveClaim(outboxId, claimId, "발행 실패 기록");
        if (outbox == null) {
            return;
        }

        outbox.incrementAttemptCount();

        if (outbox.getAttemptCount() >= MAX_RELAY_ATTEMPTS) {
            outbox.markFailed();
            submissionEventOutboxRepository.save(outbox);
            markSubmissionFailed(outbox, FailureCode.KAFKA_PROCESSING_FAILURE);
            log.error("[Judge] Outbox 재시도 상한({}) 도달 — FAILED 처리. outboxId={}, eventType={}",
                    MAX_RELAY_ATTEMPTS, outbox.getId(), outbox.getEventType());
            return;
        }

        outbox.releaseClaim();
        submissionEventOutboxRepository.save(outbox);
    }

    // Kafka 발행은 성공했으나, markPublished()의 DB 후처리가 실패했을 때 호출.
    @Transactional
    public void recordPostPublishFailure(UUID outboxId, UUID claimId) {
        SubmissionEventOutbox outbox = findActiveClaim(outboxId, claimId, "발행 후처리 실패 기록");
        if (outbox == null) {
            return;
        }

        outbox.incrementAttemptCount();

        if (outbox.getAttemptCount() >= MAX_POST_PUBLISH_FAILURE_ATTEMPTS) {
            outbox.markFailed();
            submissionEventOutboxRepository.save(outbox);
            log.error("[Judge] Kafka 발행은 성공했으나 DB 후처리(Outbox 완료/Submission 전이)가 {}회 "
                            + "연속 실패 — Submission 상태는 변경하지 않고 Outbox만 종료 처리. "
                            + "수동 확인 또는 reconciliation 필요. outboxId={}, eventType={}, aggregateId={}",
                    MAX_POST_PUBLISH_FAILURE_ATTEMPTS, outbox.getId(), outbox.getEventType(),
                    outbox.getAggregateId());
            return;
        }

        outbox.releaseClaim();
        submissionEventOutboxRepository.save(outbox);
    }

    @Transactional
    public void markUnsupportedEventType(UUID outboxId, UUID claimId) {
        SubmissionEventOutbox outbox = findActiveClaim(outboxId, claimId, "미지원 event_type 처리");
        if (outbox == null) {
            return;
        }

        outbox.markFailed();
        submissionEventOutboxRepository.save(outbox);
        markSubmissionFailed(outbox, FailureCode.INTERNAL_SYSTEM_ERROR);
        log.error("[Judge] Outbox Relay가 모르는 event_type — 즉시 FAILED 처리. outboxId={}, eventType={}",
                outbox.getId(), outbox.getEventType());
    }

    /** 지금 이 claimId로 선점 중인 Outbox를 돌려준다. 이미 처리됐거나 lease 만료 후 재선점됐다면 null(멱등하게 무시). */
    private SubmissionEventOutbox findActiveClaim(UUID outboxId, UUID claimId, String action) {
        SubmissionEventOutbox outbox = submissionEventOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "방금 발행 처리하던 Outbox를 다시 찾을 수 없습니다. outboxId=" + outboxId));
        if (!outbox.isClaimedBy(claimId)) {
            log.warn("[Judge] 유효한 선점이 아니라 {}을(를) 건너뜀(이미 처리됐거나 lease 만료 후 재선점됨). "
                    + "outboxId={}, status={}", action, outboxId, outbox.getStatus());
            return null;
        }
        return outbox;
    }

    private void markSubmissionQueued(SubmissionEventOutbox outbox) {
        Submission submission = submissionRepository.findById(outbox.getAggregateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND,
                        "Outbox가 가리키는 Submission을 찾을 수 없습니다. submissionId=" + outbox.getAggregateId()));

        try {
            submission.markQueued();
            submissionRepository.save(submission);
        } catch (BusinessException e) {
            log.warn("[Judge] Submission이 이미 QUEUED 이후 상태로 전이돼 있어 markQueued를 건너뜀. "
                    + "submissionId={}", submission.getId(), e);
        }
    }

    private void markSubmissionFailed(SubmissionEventOutbox outbox, FailureCode failureCode) {
        Submission submission = submissionRepository.findById(outbox.getAggregateId())
                .orElseThrow(()->new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND,
                        "Outbox가 가리키는 Submission을 찾을 수 없습니다. submissionId=" + outbox.getAggregateId()));
        try {
            submission.markFailed(failureCode);
            submissionRepository.save(submission);
        } catch (BusinessException e) {
            log.warn("[Judge] Submission이 이미 종료 상태로 전이돼 있어 markFailed를 건너뜀. "
                    + "submissionId={}, failureCode={}", submission.getId(), failureCode, e);
        }
    }
}