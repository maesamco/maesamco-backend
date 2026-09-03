package com.maesamco.content.dailyquiz.application.generation;

import java.util.List;

public record DailyQuizQuestionGenerationResult(
        // 생성에 성공한 문항
        List<GeneratedDailyQuizQuestion> generatedQuestions,
        // AI 호출 또는 응답 검증에 실패한 개념
        List<String> failedConcepts
) {
}
