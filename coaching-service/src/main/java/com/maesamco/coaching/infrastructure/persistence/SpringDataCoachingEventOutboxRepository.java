package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataCoachingEventOutboxRepository extends JpaRepository<CoachingEventOutbox, UUID> {

    List<CoachingEventOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
