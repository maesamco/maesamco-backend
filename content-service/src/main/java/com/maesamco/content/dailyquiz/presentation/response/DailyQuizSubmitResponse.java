package com.maesamco.content.dailyquiz.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Daily Quiz 문항 제출 및 즉시 채점 결과 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyQuizSubmitResponse(
        UUID questionVersionId,
        boolean correct,
        boolean attemptCompleted,
        Summary summary
) {

    /**
     * 마지막 문항 제출로 세트가 완료된 경우에만 제공하는 결과 요약
     */
    public record Summary(
            int correctCount,
            int totalCount
    ) {
    }
}
