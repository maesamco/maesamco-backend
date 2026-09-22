package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachingEventOutboxRepository {

    CoachingEventOutbox save(CoachingEventOutbox coachingEventOutbox);

    Optional<CoachingEventOutbox> findById(UUID id);

    /**
     * 이슈 #261 — 지금 발행할 수 있는 Outbox를 오래된 순서대로 잠그고 해당 Worker가
     * 선점합니다. PENDING(재시도 대기 시각이 지난) 또는 lease가 만료된 IN_PROGRESS 행이
     * 대상이며, 다른 트랜잭션이 잠근 행은 기다리지 않고 건너뜁니다.
     */
    List<CoachingEventOutbox> claimPublishable(
            Instant claimedAt,
            Instant leaseUntil,
            UUID claimId,
            int limit
    );
}
