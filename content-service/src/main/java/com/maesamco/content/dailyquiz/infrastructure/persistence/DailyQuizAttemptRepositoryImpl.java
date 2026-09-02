package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailyQuizAttemptRepositoryImpl implements DailyQuizAttemptRepository {

    private final SpringDataDailyQuizAttemptRepository springDataRepository;

    @Override
    public DailyQuizAttempt save(DailyQuizAttempt attempt) {
        return springDataRepository.save(attempt);
    }
}
