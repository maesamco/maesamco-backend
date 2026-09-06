package com.maesamco.content.dailyquiz.application.generation;

import java.util.Map;

public record DailyQuizQuestionGenerationResult(
        // 슬롯 번호별 생성에 성공한 문항
        Map<Integer, GeneratedDailyQuizQuestion> generatedQuestionsBySlot,
        // 슬롯 번호별 AI 호출 또는 응답 검증에 실패한 개념
        Map<Integer, String> failedConceptsBySlot
) {

    public DailyQuizQuestionGenerationResult {
        generatedQuestionsBySlot = Map.copyOf(generatedQuestionsBySlot);
        failedConceptsBySlot = Map.copyOf(failedConceptsBySlot);
    }
}
