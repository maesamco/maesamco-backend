package com.maesamco.judge.domain.repository;

import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubmissionEventOutboxRepository extends JpaRepository<SubmissionEventOutbox, UUID> {
    /** Outbox Relay 가 폴링 배치로 쓰는 조회 — 오래된 것부터 최대 100건. */
    List<SubmissionEventOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
