package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailyQuizQuestionRepositoryImpl implements DailyQuizQuestionRepository {

    private final SpringDataDailyQuizQuestionRepository springDataRepository;

    @Override
    public DailyQuizQuestion save(DailyQuizQuestion question) {
        return springDataRepository.save(question);
    }
}
