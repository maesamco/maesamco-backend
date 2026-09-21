package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionSelectionResult;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyQuizQuestionReuseService {

    private final DailyQuizQuestionRepository questionRepository;
    private final ReusableQuestionSelector questionSelector;

    public DailyQuizQuestionSelectionResult selectReusableQuestions(QuestionSlots requiredSlots) {
        List<DailyQuizQuestion> candidates = questionRepository.findActiveByQuestionSlots(requiredSlots);
        return questionSelector.select(requiredSlots, candidates);
    }
}
