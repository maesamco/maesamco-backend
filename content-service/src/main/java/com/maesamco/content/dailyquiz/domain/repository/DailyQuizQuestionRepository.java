package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;

import java.util.List;

public interface DailyQuizQuestionRepository {

    DailyQuizQuestion save(DailyQuizQuestion question);

    List<DailyQuizQuestion> findActiveByAnyConcepts(List<String> conceptTags);
}
