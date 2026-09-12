package com.maesamco.content.dailyquiz.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maesamco.content.dailyquiz.application.result.DailyQuizSubmitResult;

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

    public static DailyQuizSubmitResponse from(DailyQuizSubmitResult result) {
        Summary summary = result.attemptCompleted()
                ? new Summary(
                        result.correctCount(),
                        result.totalCount()
                )
                : null;

        return new DailyQuizSubmitResponse(
                result.questionVersionId(),
                result.correct(),
                result.attemptCompleted(),
                summary
        );
    }

    /**
     * 마지막 문항 제출로 세트가 완료된 경우에만 제공하는 결과 요약
     */
    public record Summary(
            int correctCount,
            int totalCount
    ) {
    }
}
