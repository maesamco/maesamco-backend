package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyQuizQuestionReuseService {

    private final DailyQuizQuestionRepository questionRepository;
    private final ReusableQuestionSelector questionSelector;

    public QuestionSelection selectReusableQuestions(List<String> requiredConcepts) {
        List<DailyQuizQuestion> candidates = questionRepository.findActiveByAnyConcepts(requiredConcepts);
        return questionSelector.select(requiredConcepts, candidates);
    }
}
