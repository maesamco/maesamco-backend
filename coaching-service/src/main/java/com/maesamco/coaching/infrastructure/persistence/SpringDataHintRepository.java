package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.Hint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataHintRepository extends JpaRepository<Hint, UUID> {

    List<Hint> findByCoachingSessionId(UUID coachingSessionId);

    Optional<Hint> findByCoachingSessionIdAndStage(UUID coachingSessionId, int stage);
}
