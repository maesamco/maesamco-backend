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
public interface ExplanationApiDocs {

    @Operation(
            summary = "60초 설명 등록",
            description = "본인의 정답 제출에 대해 코드 동작 원리를 설명으로 등록한다. 등록 즉시 AI가 "
                    + "역질문을 생성하며, AI 역질문 생성이 실패해도 설명 자체는 저장된다(응답의 "
                    + "followUpQuestion만 null). 이미 등록된 설명에 역질문 생성만 재시도하는 경우도 "
                    + "이 API로 처리되며 이때는 201이 아니라 200으로 응답한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "새 설명 등록"),
            @ApiResponse(responseCode = "200", description = "이미 등록된 설명의 역질문 생성만 재시도"),
            @ApiResponse(responseCode = "403", description = "EXPLANATION_NOT_ALLOWED — 본인 제출이 정답 상태가 아님"),
            @ApiResponse(responseCode = "404", description = "SUBMISSION_NOT_FOUND — 제출이 없거나 본인 소유가 아님"),
            @ApiResponse(responseCode = "409", description = "EXPLANATION_ALREADY_EXISTS — 이미 역질문까지 생성된 설명이 등록돼 있음")
    })
    ResponseEntity<SuccessResponse<ExplanationRegisterResponse>> registerExplanation(
            UUID submissionId,
            ExplanationRegisterRequest request,
            @Parameter(hidden = true) UUID userId
    );

    @Operation(
            summary = "설명·역질문·답변 조회",
            description = "본인 제출에 등록한 설명과 AI 역질문, 답변을 조회한다. 아직 답변하지 않았다면 "
                    + "followUpAnswer는 null이다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "SUBMISSION_NOT_FOUND(제출이 없거나 본인 소유가 아님) 또는 "
                    + "EXPLANATION_NOT_FOUND(등록된 설명 없음)")
    })
    ResponseEntity<SuccessResponse<ExplanationDetailResponse>> getExplanation(
            UUID submissionId,
            @Parameter(hidden = true) UUID userId
    );
}
