package com.maesamco.content.dailyquiz.application.result;

import java.util.UUID;

/**
 * Daily Quiz 문항 제출과 즉시 채점 처리 결과
 */
public record DailyQuizSubmitResult(
        UUID questionVersionId,
        boolean correct,
        boolean attemptCompleted,
        Integer correctCount,
        Integer totalCount
) {
}
