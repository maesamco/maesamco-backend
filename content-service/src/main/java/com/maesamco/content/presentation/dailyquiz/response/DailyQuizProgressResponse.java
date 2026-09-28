package com.maesamco.content.presentation.dailyquiz.response;

import com.maesamco.content.application.dailyquiz.result.DailyQuizProgressResult;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 오늘의 Daily Quiz 진행도 조회 API 응답 DTO입니다.
 */
@Schema(description = "오늘의 일일 퀴즈 진행도(세트를 시작 처리하지 않는 순수 조회)")
public record DailyQuizProgressResponse(
        @Schema(description = "오늘 세트의 현재 상태")
        DailyQuizAttemptStatus attemptStatus,
        @Schema(description = "제출을 마친 문항 수", example = "2")
        int completedCount,
        @Schema(description = "실제 배정된 전체 문항 수. 정상 5개, 일부 생성 실패 시 3~4개", example = "5")
        int totalCount
) {

    public static DailyQuizProgressResponse from(DailyQuizProgressResult result) {
        return new DailyQuizProgressResponse(
                result.attemptStatus(),
                result.completedCount(),
                result.totalCount()
        );
    }
}
