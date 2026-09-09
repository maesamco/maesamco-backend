package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * 401(AUTH_UNAUTHORIZED)은 인증 실패 시 공통으로 발생해 메서드마다 반복하지 않는다.
 */
public interface FollowUpAnswerApiDocs {

    @Operation(
            summary = "AI 역질문 답변 등록",
            description = "본인의 역질문에 답변을 등록한다. 답변 등록이 곧 코칭 세션 완료 트리거이며, "
                    + "성공 응답의 coachingSessionStatus는 항상 COMPLETED다. 답변 등록 직후 AI 종합 "
                    + "피드백 생성을 비동기로 시작하지만, 그 생성 실패는 이 응답에 영향을 주지 않는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "답변 등록 성공"),
            @ApiResponse(responseCode = "404", description = "FOLLOW_UP_QUESTION_NOT_FOUND — 역질문이 없거나 본인 소유가 아님"),
            @ApiResponse(responseCode = "409", description = "FOLLOW_UP_ANSWER_ALREADY_EXISTS — 이미 해당 역질문에 답변이 존재함")
    })
    ResponseEntity<SuccessResponse<FollowUpAnswerRegisterResponse>> registerAnswer(
            UUID followUpQuestionId,
            FollowUpAnswerRegisterRequest request,
            @Parameter(hidden = true) UUID userId
    );
}
