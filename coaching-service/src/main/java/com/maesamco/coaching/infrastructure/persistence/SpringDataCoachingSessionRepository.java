package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.CoachingSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataCoachingSessionRepository extends JpaRepository<CoachingSession, UUID> {

    Optional<CoachingSession> findBySubmissionId(UUID submissionId);

    Optional<CoachingSession> findByUserIdAndProblemIdAndStatus(UUID userId, UUID problemId, CoachingSessionStatus status);
}
