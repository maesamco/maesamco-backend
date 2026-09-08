package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CoachingEventOutboxRepositoryImpl implements CoachingEventOutboxRepository {

    private final SpringDataCoachingEventOutboxRepository springDataCoachingEventOutboxRepository;

    @Override
    public CoachingEventOutbox save(CoachingEventOutbox coachingEventOutbox) {
        return springDataCoachingEventOutboxRepository.save(coachingEventOutbox);
    }

    @Override
    public Optional<CoachingEventOutbox> findById(UUID id) {
        return springDataCoachingEventOutboxRepository.findById(id);
    }

    @Override
    public List<CoachingEventOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status) {
        return springDataCoachingEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(status);
    }
}
