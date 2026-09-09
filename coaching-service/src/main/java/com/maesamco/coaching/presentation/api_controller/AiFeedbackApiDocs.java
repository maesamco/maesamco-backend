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
public interface AiFeedbackApiDocs {

    @Operation(
            summary = "AI 종합 피드백 조회",
            description = "본인 제출의 코칭 세션이 완료된 뒤 생성된 AI 종합 피드백을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SUBMISSION_NOT_FOUND(제출이 없거나 본인 소유가 아님), "
                    + "AI_FEEDBACK_NOT_STARTED(코칭 세션이 아직 완료되지 않음), "
                    + "AI_FEEDBACK_NOT_FOUND(생성 시도는 있었지만 재시도 예산이 남아 있는 채로 아직 없음) 중 하나"),
            @ApiResponse(responseCode = "409", description = "AI_FEEDBACK_RETRY_LIMIT_EXCEEDED — 재시도 예산을 모두 소진한 채로 아직 생성되지 않음")
    })
    ResponseEntity<SuccessResponse<AiFeedbackResponse>> getFeedback(
            UUID submissionId,
            @Parameter(hidden = true) UUID userId
    );

    @Operation(
            summary = "AI 종합 피드백 재시도",
            description = "이전 생성이 실패한 AI 종합 피드백을 재시도한다. 세션당 재시도 3회(최초 1회 "
                    + "포함 총 4회)로 제한되며, 동시 재시도 요청은 세션 단위 락으로 직렬화된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재시도 성공"),
            @ApiResponse(responseCode = "404", description = "SUBMISSION_NOT_FOUND(제출이 없거나 본인 소유가 아님) 또는 "
                    + "AI_FEEDBACK_NOT_FOUND(세션이 아직 완료 전이라 재시도할 생성 시도 자체가 없음)"),
            @ApiResponse(responseCode = "409", description = "AI_FEEDBACK_RETRY_IN_PROGRESS(동시 재시도 요청 중), "
                    + "AI_FEEDBACK_ALREADY_EXISTS(이미 피드백이 생성돼 있음), "
                    + "AI_FEEDBACK_RETRY_LIMIT_EXCEEDED(재시도 횟수 초과) 중 하나")
    })
    ResponseEntity<SuccessResponse<AiFeedbackResponse>> retryFeedback(
            UUID submissionId,
            @Parameter(hidden = true) UUID userId
    );
}
