package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.Explanation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataExplanationRepository extends JpaRepository<Explanation, UUID> {

    Optional<Explanation> findByCoachingSessionId(UUID coachingSessionId);
}
