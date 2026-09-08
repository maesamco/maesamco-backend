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
public interface WeakConceptApiDocs {

    @Operation(
            summary = "내 취약 개념 목록 조회",
            description = "로그인한 사용자 본인의 취약 개념 목록을 조회한다. 발견 횟수가 높을수록, "
                    + "복습 후 개선되지 않았을수록 우선순위가 높은 취약 개념이다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 — 취약 개념이 없으면 빈 배열")
    })
    ResponseEntity<SuccessResponse<List<WeakConceptResponse>>> getWeakConcepts(
            @Parameter(hidden = true) UUID userId
    );
}
