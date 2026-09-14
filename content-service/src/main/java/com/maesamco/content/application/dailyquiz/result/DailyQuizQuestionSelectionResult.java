package com.maesamco.content.application.dailyquiz.result;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;

import java.util.Map;

public record DailyQuizQuestionSelectionResult(
        // 슬롯 번호별 문제은행에서 찾은 문항
        Map<Integer, DailyQuizQuestion> selectedQuestionsBySlot,
        // 슬롯 번호별 문제은행에서 찾지 못해 AI 생성이 필요한 개념
        Map<Integer, String> missingConceptsBySlot
) {

    public DailyQuizQuestionSelectionResult {
        selectedQuestionsBySlot = Map.copyOf(selectedQuestionsBySlot);
        missingConceptsBySlot = Map.copyOf(missingConceptsBySlot);
    }
}
