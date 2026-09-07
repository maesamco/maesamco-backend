package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.application.result.DailyQuizQuestionSelectionResult;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.REUSABLE_CANDIDATE_LIMIT_PER_CONCEPT;

@Service
@RequiredArgsConstructor
public class DailyQuizQuestionReuseService {

    private final DailyQuizQuestionRepository questionRepository;
    private final ReusableQuestionSelector questionSelector;

    public DailyQuizQuestionSelectionResult selectReusableQuestions(List<String> requiredConcepts) {
        List<DailyQuizQuestion> candidates = questionRepository.findActiveByAnyConcepts(
                requiredConcepts,
                REUSABLE_CANDIDATE_LIMIT_PER_CONCEPT
        );
        return questionSelector.select(requiredConcepts, candidates);
    }
}
