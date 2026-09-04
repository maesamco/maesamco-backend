package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CoachingEventOutboxRepositoryImpl implements CoachingEventOutboxRepository {

    private final SpringDataCoachingEventOutboxRepository springDataCoachingEventOutboxRepository;

    @Override
    public CoachingEventOutbox save(CoachingEventOutbox coachingEventOutbox) {
        return springDataCoachingEventOutboxRepository.save(coachingEventOutbox);
    }
}
