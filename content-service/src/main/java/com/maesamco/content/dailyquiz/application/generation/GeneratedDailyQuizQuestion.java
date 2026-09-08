package com.maesamco.content.dailyquiz.application.generation;

import com.maesamco.content.aigeneration.application.AiGenerationMetadata;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizProblemType;

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
