package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CoachingSessionRepositoryImpl implements CoachingSessionRepository {

    private final SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Override
    public CoachingSession save(CoachingSession coachingSession) {
        return springDataCoachingSessionRepository.save(coachingSession);
    }

    @Override
    public Optional<CoachingSession> findById(UUID id) {
        return springDataCoachingSessionRepository.findById(id);
    }

    @Override
    public Optional<CoachingSession> findBySubmissionId(UUID submissionId) {
        return springDataCoachingSessionRepository.findBySubmissionId(submissionId);
    }
}
