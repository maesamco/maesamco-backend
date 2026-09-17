package com.maesamco.judge.presentation.api_controller;

import com.maesamco.judge.application.result.SubmissionSummaryResult;
import com.maesamco.judge.global.response.PageResponse;
import com.maesamco.judge.global.response.SuccessResponse;
import com.maesamco.judge.presentation.request.SubmissionCreateRequest;
import com.maesamco.judge.presentation.response.SubmissionCreateResponse;
import com.maesamco.judge.presentation.response.SubmissionExternalGetResponse;
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

    @Operation(
            summary = "채점 상태·결과 조회",
            description = "제출 상태와 채점 결과를 조회한다. 본인 제출만 조회 가능하며, "
                    + "존재하지 않거나 본인 제출이 아닌 경우 구분 없이 404로 응답한다(IDOR 방지). "
                    + "submissionId/problemId/problemVersionId/attemptNo/status/submittedAt은 상태와 무관하게 항상 포함되며, "
                    + "상태별로 추가되는 필드만 다르다 — "
                    + "진행 중(PENDING/QUEUED/RUNNING/RETRY_WAIT): 추가 필드 없음 / "
                    + "COMPLETED: result+testResults+judgedAt+executionTimeMs+memoryUsedKb / "
                    + "FAILED: failureCode+judgedAt."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "SUBMISSION_NOT_FOUND — 존재하지 않거나 본인 제출이 아님")
    })
    ResponseEntity<SuccessResponse<SubmissionExternalGetResponse>> getSubmission(
            @Parameter(name = "submissionId", required = true) UUID submissionId,
            @Parameter(hidden = true) UUID userId
    );

    @Operation(
            summary = "내 제출·재도전 이력 목록 조회",
            description = "로그인한 사용자의 제출 이력을 페이지 단위로 조회한다. "
                    + "problemId로 필터링할 수 있다. "
                    + "size가 유효하지 않으면 기본값(20)으로, direction이 유효하지 않으면 기본값(DESC)으로 조용히 대체된다. "
                    + "sort는 비어 있으면 기본값(createdAt)으로 대체되지만, 존재하지 않는 필드명을 지정하면 "
                    + "400(INVALID_SORT_PROPERTY)으로 응답한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_SORT_PROPERTY — 존재하지 않는 정렬 필드"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청")
    })
    ResponseEntity<SuccessResponse<PageResponse<SubmissionSummaryResult>>> getMySubmissions(
            @Parameter(hidden = true) UUID userId,
            @Parameter(name = "problemId", description = "특정 문제로 필터링 (선택)") UUID problemId,
            @Parameter(name = "page", description = "페이지 번호, 0-indexed (기본값 0)") Integer page,
            @Parameter(name = "size", description = "페이지 크기 (기본값 20, 최대 100)") Integer size,
            @Parameter(name = "sort", description = "정렬 기준 프로퍼티 (기본값 createdAt)") String sort,
            @Parameter(name = "direction", description = "정렬 방향 ASC/DESC (기본값 DESC)") String direction
    );
}
