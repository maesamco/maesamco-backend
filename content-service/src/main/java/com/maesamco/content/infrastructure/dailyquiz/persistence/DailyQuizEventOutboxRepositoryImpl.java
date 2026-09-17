package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailyQuizEventOutboxRepositoryImpl implements DailyQuizEventOutboxRepository {

    private final SpringDataDailyQuizEventOutboxRepository springDataRepository;

    @Override
    public DailyQuizEventOutbox save(DailyQuizEventOutbox outbox) {
        return springDataRepository.save(outbox);
    }
}
