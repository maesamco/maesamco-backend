package com.maesamco.judge.application.persistence_service;

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

/**
 * Outbox Relay(Facade)가 Kafka 호출 사이사이에 배치하는 짧은 DB 트랜잭션 조각
 * (팀 컨벤션 2절 Facade — persistence_service). Facade엔 @Transactional을 안 붙이고
 * 실제 DB 쓰기만 여기서 별도 트랜잭션으로 커밋합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionEventOutboxPersistenceService {
    private final SubmissionEventOutboxRepository submissionEventOutboxRepository;
    private final SubmissionRepository submissionRepository;

    // Kafka 발행 성공 후 호출 — Outbox를 COMPLETED로 표시하는 것과 Submission을
    // PENDING -> QUEUED로 전이시키는 것을 같은 트랜잭션으로 묶음.
    @Transactional
    public void markPublished(SubmissionEventOutbox outbox) {
        outbox.incrementAttemptCount();
        outbox.markPublished();
        submissionEventOutboxRepository.save(outbox);

        markSubmissionQueued(outbox);
    }

    @Transactional
    public void recordFailedAttempt(SubmissionEventOutbox outbox) {
        outbox.incrementAttemptCount();
        submissionEventOutboxRepository.save(outbox);
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
}