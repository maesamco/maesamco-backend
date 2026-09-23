package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.response.ProblemProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Controller
@RequestMapping("/api/v1/contents/problem-progress")
@Tag(name = "Problem Progress", description = "사용자 문제 풀이 진행 이력 조회 API")
@SecurityRequirement(name = "bearerAuth")
public interface ProblemProgressApiDocs {

    @GetMapping
    @Operation(
            summary = "내 문제 풀이 이력 목록 조회",
            description = "현재 인증된 사용자의 문제 풀이 이력을 페이징하여 조회합니다. "
                    + "progressStatus를 생략하면 전체 이력을 조회하고 전달하면 해당 상태의 이력만 조회합니다. "
                    + "조회 결과는 최근 생성된 이력부터 반환되며 다른 사용자의 이력은 조회할 수 없습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문제 풀이 이력 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "풀이 상태 또는 페이징 요청 값이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청")
    })
    ResponseEntity<SuccessResponse<PageResponse<ProblemProgressResponse>>> getProblemProgresses(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UUID userId,

            @Parameter(
                    name = "progressStatus",
                    description = "조회할 문제 풀이 상태이며 생략하면 전체 풀이 이력을 조회합니다.",
                    in = ParameterIn.QUERY
            )
            @RequestParam(required = false) ProblemProgressStatus progressStatus,

            @Parameter(
                    name = "page",
                    description = "조회할 페이지 번호",
                    in = ParameterIn.QUERY,
                    schema = @Schema(
                            type = "integer",
                            defaultValue = "0",
                            minimum = "0"
                    )
            )
            @RequestParam(required = false) Integer page,

            @Parameter(
                    name = "size",
                    description = "한 페이지에 조회할 문제 풀이 이력 개수",
                    in = ParameterIn.QUERY,
                    schema = @Schema(
                            type = "integer",
                            defaultValue = "20",
                            minimum = "1",
                            maximum = "100"
                    )
            )
            @RequestParam(required = false) Integer size
    );

    @GetMapping("/{problemId}")
    @Operation(
            summary = "특정 문제의 내 풀이 이력 조회",
            description = "현재 인증된 사용자의 특정 문제에 대한 풀이 이력을 조회합니다. "
                    + "현재 사용자 ID와 문제 ID를 기준으로 조회하며 다른 사용자의 이력은 조회할 수 없습니다. "
                    + "해당 문제를 한 번도 풀이하지 않은 경우 풀이 이력을 조회할 수 없습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문제 풀이 이력 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "해당 문제에 대한 사용자의 풀이 이력을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<ProblemProgressResponse>> getProblemProgress(
            @Parameter(description = "풀이 이력을 조회할 문제 ID")
            @PathVariable UUID problemId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal UUID userId
    );
}