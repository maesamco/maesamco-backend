package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataCoachingSessionRepository extends JpaRepository<CoachingSession, UUID> {

    Optional<CoachingSession> findBySubmissionId(UUID submissionId);
}
