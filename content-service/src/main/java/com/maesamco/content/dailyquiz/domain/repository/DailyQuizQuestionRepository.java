package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;

public interface DailyQuizQuestionRepository {

    DailyQuizQuestion save(DailyQuizQuestion question);
}
