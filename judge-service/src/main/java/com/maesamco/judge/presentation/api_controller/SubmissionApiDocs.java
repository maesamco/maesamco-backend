package com.maesamco.judge.presentation.api_controller;

import com.maesamco.judge.global.response.SuccessResponse;
import com.maesamco.judge.presentation.request.SubmissionCreateRequest;
import com.maesamco.judge.presentation.response.SubmissionCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

public interface SubmissionApiDocs {
    @Operation(
            summary = "코드 제출",
            description = "Java 코드를 제출하고 비동기 채점을 요청한다. "
                    + "제출은 PENDING 상태로 저장 및 Outbox 기록까지만 마치고 즉시 202로 응답하며, "
                    + "실제 채점 결과(Judge0 실행)는 이 요청과 분리된 별도 파이프라인에서 처리된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수 성공 — submissionId와 PENDING 상태 반환"),
            @ApiResponse(responseCode = "404", description = "PROBLEM_NOT_FOUND — 존재하지 않거나 아직 발행되지 않은 problemId"),
            @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_CONFLICT — 동일 키로 다른 요청 바디")
    })
    ResponseEntity<SuccessResponse<SubmissionCreateResponse>> create(
            @Parameter(hidden = true) UUID userId,
            @Parameter(name = "Idempotency-Key", required = true) String idempotencyKey,
            SubmissionCreateRequest request
    );
}
