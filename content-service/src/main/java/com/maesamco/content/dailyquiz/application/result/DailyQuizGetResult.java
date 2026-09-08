package com.maesamco.content.dailyquiz.application.result;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 인증된 사용자의 오늘 Daily Quiz 세트 조회 결과 DTO
 */
public record DailyQuizGetResult(
        // 오늘의 Daily Quiz 세트 ID
        UUID quizAttemptId,
        // 조회 및 시작 처리가 반영된 세트 상태
        DailyQuizAttemptStatus attemptStatus,
        // 세트에 실제로 배정된 전체 문항 수
        int totalCount,
        // 사용자가 세트를 최초로 시작한 시각
        Instant startedAt,
        // 문항 순서대로 정렬된 배정 문항 조회 결과
        List<DailyQuizQuestionGetResult> questions
) {
}
