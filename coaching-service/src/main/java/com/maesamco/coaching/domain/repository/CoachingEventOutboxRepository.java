package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachingEventOutboxRepository {

    CoachingEventOutbox save(CoachingEventOutbox coachingEventOutbox);

    Optional<CoachingEventOutbox> findById(UUID id);

    /** Relay Worker가 폴링 배치로 쓰는 조회 — 오래된 것부터 최대 100건. */
    List<CoachingEventOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
