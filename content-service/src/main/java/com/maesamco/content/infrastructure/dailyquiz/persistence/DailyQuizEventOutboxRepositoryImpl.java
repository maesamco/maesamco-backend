package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DailyQuizEventOutboxRepositoryImpl implements DailyQuizEventOutboxRepository {

    private final SpringDataDailyQuizEventOutboxRepository springDataRepository;

    @Override
    public DailyQuizEventOutbox save(DailyQuizEventOutbox outbox) {
        return springDataRepository.save(outbox);
    }

    @Override
    public Optional<DailyQuizEventOutbox> findById(UUID id) {
        return springDataRepository.findById(id);
    }

    @Override
    public List<DailyQuizEventOutbox> findPublishablePending(Instant availableAt, int limit) {
        return springDataRepository.findPublishableByStatus(
                DailyQuizEventOutboxStatus.PENDING,
                availableAt,
                PageRequest.of(0, limit)
        );
    }
}
