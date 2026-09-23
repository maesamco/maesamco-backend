package com.maesamco.content.presentation.dailyquiz;

import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.dailyquiz.request.DailyQuizSubmitRequest;
import com.maesamco.content.presentation.dailyquiz.response.DailyQuizGetResponse;
import com.maesamco.content.presentation.dailyquiz.response.DailyQuizSubmitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.UUID;

public interface DailyQuizApiDocs {

    @Operation(
            summary = "오늘의 일일 퀴즈 조회",
            description = "Access Token으로 인증된 사용자의 오늘 세트를 조회합니다. "
                    + "READY 상태에서 처음 조회하면 IN_PROGRESS로 전환하고 시작 시각을 기록합니다. "
                    + "실제 문항 수는 3~5개이며 180초는 제출 제한이 아닌 권장 시간입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 문항별 답변 및 채점 상태 반환"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "QUIZ_NOT_FOUND — 오늘 생성된 세트가 없음")
    })
    SuccessResponse<DailyQuizGetResponse> getDailyQuiz(
            @Parameter(hidden = true) UUID userId
    );

    @Operation(
            summary = "일일 퀴즈 문항 제출",
            description = "인증된 사용자가 자신에게 배정된 문항 하나를 제출하고 즉시 채점합니다. "
                    + "마지막 문항 제출 시 세트가 완료되며 결과 요약을 반환합니다. "
                    + "권장 시간 180초가 지나도 제출할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 성공 — 마지막 문항에서만 summary 반환"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — 답안 누락·공백·200자 초과"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "QUIZ_NOT_FOUND — 세트가 없거나 본인 소유가 아님; "
                    + "QUESTION_NOT_ASSIGNED — 세트에 배정되지 않은 문항"),
            @ApiResponse(responseCode = "409", description = "ALREADY_SUBMITTED — 이미 제출한 문항; "
                    + "INVALID_QUIZ_STATUS — 제출할 수 없는 세트 상태"),
            @ApiResponse(responseCode = "410", description = "QUIZ_EXPIRED — 이전 날짜의 세트")
    })
    SuccessResponse<DailyQuizSubmitResponse> submitQuestion(
            @Parameter(hidden = true) UUID userId,
            @Parameter(description = "일일 퀴즈 세트 ID") UUID quizAttemptId,
            @Parameter(description = "배정된 문항 버전 ID") UUID questionVersionId,
            DailyQuizSubmitRequest request
    );
}
