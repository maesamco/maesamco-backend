package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.CoachingSession;

import java.util.Optional;
import java.util.UUID;

public interface CoachingSessionRepository {

    CoachingSession save(CoachingSession coachingSession);

    Optional<CoachingSession> findById(UUID id);

    Optional<CoachingSession> findBySubmissionId(UUID submissionId);
}
