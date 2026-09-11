package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

/**
 * 401(AUTH_UNAUTHORIZED)은 인증 실패 시 공통으로 발생해 메서드마다 반복하지 않는다.
 */
public interface HintApiDocs {

    @Operation(
            summary = "오답 단계별 힌트 요청",
            description = "본인의 오답 제출에 대해 다음 단계 힌트를 요청한다. 이미 해당 단계의 힌트가 "
                    + "존재하면 새로 생성하지 않고 기존 힌트를 그대로 반환한다. 같은 문제를 다른 접근으로 "
                    + "재도전하는 것 자체는 허용되지만, 코칭 세션이 이미 완료된 뒤에는 새 힌트를 생성하지 "
                    + "않는다 — 이전 힌트는 힌트 목록 조회 API로 계속 볼 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "새 힌트 생성"),
            @ApiResponse(responseCode = "200", description = "이미 존재하는 단계의 힌트를 그대로 반환"),
            @ApiResponse(responseCode = "403", description = "HINT_NOT_ALLOWED — 본인 제출이 오답 상태가 아님"),
            @ApiResponse(responseCode = "404", description = "SUBMISSION_NOT_FOUND — 제출이 없거나 본인 소유가 아님"),
            @ApiResponse(responseCode = "409", description = "COACHING_SESSION_ALREADY_COMPLETED — 이미 완료된 코칭 세션이라 새 힌트를 생성할 수 없음"),
            @ApiResponse(responseCode = "503", description = "AI_GENERATION_FAILED — 힌트 생성 실패, 잠시 후 재시도")
    })
    ResponseEntity<SuccessResponse<HintResponse>> requestHint(
            UUID submissionId,
            @Parameter(hidden = true) UUID userId
    );

    @Operation(
            summary = "힌트 목록 조회",
            description = "본인 제출에 대해 지금까지 생성된 힌트를 단계 순서대로 조회한다. "
                    + "아직 요청한 적 없으면 빈 목록을 반환한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 힌트가 없으면 빈 배열"),
            @ApiResponse(responseCode = "404", description = "SUBMISSION_NOT_FOUND — 제출이 없거나 본인 소유가 아님")
    })
    ResponseEntity<SuccessResponse<List<HintListItemResponse>>> getHints(
            UUID submissionId,
            @Parameter(hidden = true) UUID userId
    );
}
