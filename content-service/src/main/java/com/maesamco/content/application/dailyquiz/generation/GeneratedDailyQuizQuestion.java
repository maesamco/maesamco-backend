package com.maesamco.content.application.dailyquiz.generation;

import com.maesamco.content.application.aigeneration.AiGenerationMetadata;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;

import java.util.List;

public record GeneratedDailyQuizQuestion(
        DailyQuizProblemType problemType,
        String questionText,
        List<String> choices,
        String answer,
        List<String> allowedAnswerVariants,
        List<String> conceptTags,
        AiGenerationMetadata generationMetadata
) {
}
