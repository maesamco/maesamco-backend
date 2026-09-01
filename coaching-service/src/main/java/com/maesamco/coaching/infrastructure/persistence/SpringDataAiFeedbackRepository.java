package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataAiFeedbackRepository extends JpaRepository<AiFeedback, UUID> {

    Optional<AiFeedback> findByCoachingSessionId(UUID coachingSessionId);
}
