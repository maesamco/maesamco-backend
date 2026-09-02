package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;

import java.util.List;

public record QuestionSelection(
        // 문제은행에서 찾은 문항 목록
        List<DailyQuizQuestion> selectedQuestions,
        // 문제은행에서 찾지 못해 AI 생성이 필요한 개념 목록
        List<String> missingConcepts
) {
}
