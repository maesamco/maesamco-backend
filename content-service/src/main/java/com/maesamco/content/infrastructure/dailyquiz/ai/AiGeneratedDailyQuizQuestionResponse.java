package com.maesamco.content.infrastructure.dailyquiz.ai;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;

import java.util.List;

public record AiGeneratedDailyQuizQuestionResponse(
        DailyQuizProblemType problemType,
        String questionText,
        List<String> choices,
        String answer,
        List<String> allowedAnswerVariants
) {
}
