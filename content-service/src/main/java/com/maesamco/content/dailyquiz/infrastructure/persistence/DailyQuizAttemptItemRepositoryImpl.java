package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailyQuizAttemptItemRepositoryImpl implements DailyQuizAttemptItemRepository {

    private final SpringDataDailyQuizAttemptItemRepository springDataRepository;

    @Override
    public DailyQuizAttemptItem save(DailyQuizAttemptItem attemptItem) {
        return springDataRepository.save(attemptItem);
    }
}
