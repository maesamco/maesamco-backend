package com.maesamco.content.application.dailyquiz.result;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptStatus;

/**
 * 오늘의 Daily Quiz 진행도 조회 결과 DTO입니다.
 *
 * {@link com.maesamco.content.application.dailyquiz.query_service.DailyQuizGetQueryService}와
 * 달리 세트를 시작 처리(READY→IN_PROGRESS)하지 않는 순수 조회 전용입니다.
 */
public record DailyQuizProgressResult(
        DailyQuizAttemptStatus attemptStatus,
        int completedCount,
        int totalCount
) {
}
