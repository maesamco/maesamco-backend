package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationException;
import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationResult;
import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerator;
import com.maesamco.content.dailyquiz.application.generation.GeneratedDailyQuizQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyQuizQuestionGenerationService {

    private final DailyQuizQuestionGenerator questionGenerator;

    public DailyQuizQuestionGenerationResult generateMissingQuestions(
            List<String> missingConcepts
    ) {
        List<GeneratedDailyQuizQuestion> generatedQuestions = new ArrayList<>();
        List<String> failedConcepts = new ArrayList<>();

        for (String concept : missingConcepts) {
            try {
                GeneratedDailyQuizQuestion generatedQuestion = questionGenerator.generate(concept);

                generatedQuestions.add(generatedQuestion);
            } catch (DailyQuizQuestionGenerationException exception) {
                log.warn("Daily Quiz AI 문항 생성 실패. conceptTag={}", concept, exception);
                failedConcepts.add(concept);
            }
        }

        return new DailyQuizQuestionGenerationResult(
                generatedQuestions,
                failedConcepts
        );
    }
}
