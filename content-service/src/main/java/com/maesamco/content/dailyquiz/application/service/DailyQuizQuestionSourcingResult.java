package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;

import java.util.List;

import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.MINIMUM_QUESTION_COUNT;
import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.TARGET_QUESTION_COUNT;

public record DailyQuizQuestionSourcingResult(
        // 문제 은행에서 재사용 문항과 AI 생성 후 저장된 문항을 합친 목록
        List<DailyQuizQuestion> questions,
        // AI 생성에도 실패해 끝까지 채우지 못한 개념
        List<String> failedConcepts
) {
    public boolean canCreateQuiz() {
        return questions.size() >= MINIMUM_QUESTION_COUNT;
    }

    // 최소 문항은 확보했지만 목표 문항 수에는 미달한 fallback 세트인지 확인합니다.
    public boolean isFallback() {
        return canCreateQuiz()
                && questions.size() < TARGET_QUESTION_COUNT;
    }
}
