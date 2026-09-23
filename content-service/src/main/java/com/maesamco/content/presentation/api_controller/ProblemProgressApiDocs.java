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
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * ProblemProgress API Swagger 문서입니다.
 *
 * <p>현재 인증된 사용자의 문제 풀이 진행 이력을 조회하는
 * API 명세를 제공합니다.</p>
 */
public interface ProblemProgressApiDocs {

    @Operation(
            summary = "내 문제 풀이 이력 목록 조회",
            description = """
                    현재 인증된 사용자의 문제 풀이 이력을 페이징하여 조회합니다.

                    progressStatus를 전달하지 않으면 전체 풀이 이력을 조회하고,
                    progressStatus를 전달하면 해당 상태의 풀이 이력만 조회합니다.

                    조회 결과는 최근 생성된 이력부터 반환됩니다.
                    page와 size를 생략하면 기본 페이징 값이 적용됩니다.

                    인증된 사용자만 요청할 수 있으며,
                    다른 사용자의 풀이 이력은 조회할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 풀이 이력 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "풀이 상태 또는 페이징 요청 값이 올바르지 않음"
            )
    })
    ResponseEntity<SuccessResponse<PageResponse<ProblemProgressResponse>>> getProblemProgresses(
            @Parameter(hidden = true)
            UUID userId,

            @Parameter(
                    name = "progressStatus",
                    description = """
                            조회할 문제 풀이 상태입니다.
                            생략하면 현재 사용자의 전체 풀이 이력을 조회합니다.
                            """,
                    in = ParameterIn.QUERY
            )
            ProblemProgressStatus progressStatus,

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
            Integer page,

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
            Integer size
    );

    @Operation(
            summary = "특정 문제의 내 풀이 이력 조회",
            description = """
                    현재 인증된 사용자의 특정 문제에 대한 풀이 이력을 조회합니다.

                    현재 로그인한 사용자 ID와 문제 ID를 함께 사용해
                    해당 사용자의 풀이 진행 이력을 조회합니다.

                    사용자가 해당 문제를 한 번도 풀이하지 않은 경우
                    풀이 이력을 조회할 수 없습니다.

                    인증된 사용자만 요청할 수 있으며,
                    다른 사용자의 풀이 이력은 조회할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 풀이 이력 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 문제에 대한 사용자의 풀이 이력을 찾을 수 없음"
            )
    })
    ResponseEntity<SuccessResponse<ProblemProgressResponse>> getProblemProgress(
            @Parameter(
                    description = "풀이 이력을 조회할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId,

            @Parameter(hidden = true)
            UUID userId
    );
}