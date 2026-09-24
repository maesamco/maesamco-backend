package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.ProblemSearchRequest;
import com.maesamco.content.presentation.response.AdminProblemSearchItemResponse;
import com.maesamco.content.presentation.response.ProblemVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/api/v1/admin/contents/problems")
@Tag(name = "Admin Problem", description = "관리자 문제 발행 및 재발행 API")
@SecurityRequirement(name = "bearerAuth")
public interface AdminProblemApiDocs {

    @GetMapping
    @Operation(
            summary = "관리자 문제 목록 조회",
            description = "문제 상태를 포함한 검색 조건으로 문제 목록을 조회합니다. "
                    + "상태를 지정하지 않으면 ARCHIVED를 포함한 모든 상태를 조회하며, ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 문제 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    ResponseEntity<
            SuccessResponse<
                    PageResponse<AdminProblemSearchItemResponse>
                    >
            > searchProblems(
            @Valid @ModelAttribute ProblemSearchRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    );

    @GetMapping("/{problemId}/versions")
    @Operation(
            summary = "문제 버전 이력 조회",
            description = "특정 문제의 전체 버전 이력을 최신 버전부터 조회합니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문제 버전 이력 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "PROBLEM_NOT_FOUND — 문제를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<List<ProblemVersionResponse>>> getProblemVersions(
            @Parameter(description = "조회할 문제 ID")
            @PathVariable UUID problemId
    );

    @GetMapping("/{problemId}/versions/{versionNo}")
    @Operation(
            summary = "문제 특정 버전 조회",
            description = "특정 문제의 버전 번호에 해당하는 스냅샷을 조회합니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문제 특정 버전 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "PROBLEM_NOT_FOUND — 문제를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<ProblemVersionResponse>> getProblemVersion(
            @Parameter(description = "조회할 문제 ID")
            @PathVariable UUID problemId,
            @Parameter(description = "조회할 문제 버전 번호")
            @PathVariable Integer versionNo
    );

    @PostMapping("/{problemId}/approve")
    @Operation(
            summary = "문제 발행 승인",
            description =
                    "REVIEW_PENDING 상태의 문제 발행을 승인합니다. "
                            + "승인 시 문제 상태를 PUBLISHED로 변경하고, 현재 문제와 테스트케이스 기준의 ProblemVersion을 생성합니다. "
                            + "동일 트랜잭션에서 PROBLEM_PUBLISHED 이벤트를 Outbox에 기록하며, ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문제 발행 승인 및 발행 이벤트 기록 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "PROBLEM_NOT_FOUND — 문제를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<Void>> approvePublication(
            @Parameter(description = "발행을 승인할 문제 ID")
            @PathVariable UUID problemId
    );

    @PostMapping("/{problemId}/republish")
    @Operation(
            summary = "문제 재발행 준비",
            description = "PUBLISHED 상태의 문제를 다시 발행할 수 있도록 REVIEW_PENDING 상태로 되돌립니다. "
                    + "기존 문제를 삭제하거나 새로 생성하지 않고 동일한 문제를 다시 발행하기 위한 복구 경로입니다. "
                    + "이후 문제 발행 승인 API를 다시 호출하면 새로운 ProblemVersion과 PROBLEM_PUBLISHED 이벤트가 Outbox에 기록됩니다. "
                    + "Kafka 장애 또는 이벤트 처리 실패 시 다른 서비스에 이벤트를 다시 반영할 수 있으며, ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문제를 REVIEW_PENDING 상태로 변경 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "PROBLEM_NOT_FOUND — 문제를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<Void>> revertToReviewPendingForRepublish(
            @Parameter(description = "재발행할 문제 ID")
            @PathVariable UUID problemId
    );
}
