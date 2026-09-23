package com.maesamco.content.presentation.dailyquiz.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSubmitResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Daily Quiz 문항 제출 및 즉시 채점 결과 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "문항 제출 및 즉시 채점 결과")
public record DailyQuizSubmitResponse(
        @Schema(description = "제출한 문항 버전 ID")
        UUID questionVersionId,
        @Schema(description = "제출한 답안의 정답 여부")
        boolean correct,
        @Schema(description = "이번 제출로 세트가 완료됐는지 여부")
        boolean attemptCompleted,
        @Schema(description = "마지막 문항 제출로 세트가 완료된 경우에만 제공")
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
    @Schema(description = "완료된 세트의 채점 요약")
    public record Summary(
            @Schema(description = "맞힌 문항 수")
            int correctCount,
            @Schema(description = "세트의 실제 전체 문항 수")
            int totalCount
    ) {
    }
}
