package com.maesamco.content.dailyquiz.infrastructure.ai;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizProblemType;

import java.util.List;

public record AiGeneratedDailyQuizQuestionResponse(
        DailyQuizProblemType problemType,
        String questionText,
        List<String> choices,
        String answer,
        List<String> allowedAnswerVariants
) {
}
