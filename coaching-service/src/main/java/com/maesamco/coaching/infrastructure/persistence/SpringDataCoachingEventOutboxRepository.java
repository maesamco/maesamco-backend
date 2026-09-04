package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataCoachingEventOutboxRepository extends JpaRepository<CoachingEventOutbox, UUID> {
}
