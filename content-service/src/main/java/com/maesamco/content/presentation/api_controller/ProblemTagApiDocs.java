package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.response.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * ProblemTag API Swagger 문서입니다.
 *
 * <p>문제와 태그의 연결 조회, 등록, 제거 API에 대한 명세를 제공합니다.</p>
 *
 * <p>태그 목록 조회는 공개 API이며,
 * 태그 등록과 제거는 ADMIN 권한이 필요합니다.</p>
 */
public interface ProblemTagApiDocs {

    @Operation(
            summary = "문제 태그 목록 조회",
            description = """
                    특정 문제에 연결된 태그 목록을 페이징하여 조회합니다.

                    PUBLISHED 상태의 문제에 연결된 태그만 조회할 수 있으며,
                    존재하지 않거나 공개되지 않은 문제는 조회할 수 없습니다.

                    page와 size를 생략하면 기본 페이징 값이 적용됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 태그 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            PROBLEM_NOT_FOUND
                            - 문제를 찾을 수 없음
                            - PUBLISHED 상태가 아닌 문제는 공개 조회할 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<PageResponse<TagResponse>>> getProblemTags(
            @Parameter(
                    description = "태그 목록을 조회할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId,

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
                    description = "한 페이지에 조회할 태그 개수",
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
            summary = "문제에 태그 등록",
            description = """
                    문제에 지정한 태그를 연결합니다.

                    등록 전 문제와 태그의 존재 여부를 확인하며,
                    이미 동일한 문제와 태그가 연결되어 있는 경우
                    중복으로 등록할 수 없습니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "문제 태그 등록 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            연결할 리소스를 찾을 수 없음
                            - PROBLEM_NOT_FOUND: 문제를 찾을 수 없음
                            - TAG_NOT_FOUND: 태그를 찾을 수 없음
                            """
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            PROBLEM_TAG_ALREADY_EXISTS
                            - 해당 문제와 태그가 이미 연결되어 있음
                            """
            )
    })
    ResponseEntity<SuccessResponse<Void>> addTagToProblem(
            @Parameter(
                    description = "태그를 등록할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId,

            @Parameter(
                    description = "문제에 등록할 태그 ID",
                    required = true,
                    example = "7a9e6679-7425-40de-944b-e07fc1f90ae7"
            )
            UUID tagId
    );

    @Operation(
            summary = "문제 태그 연결 제거",
            description = """
                    문제와 태그 사이의 연결을 제거합니다.

                    Problem 또는 Tag 자체를 삭제하는 것이 아니라,
                    두 리소스 사이의 연결 정보만 제거합니다.

                    해당 문제와 태그의 연결이 존재하지 않으면
                    PROBLEM_TAG_NOT_FOUND 오류가 발생합니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 태그 연결 제거 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            PROBLEM_TAG_NOT_FOUND
                            - 해당 문제와 태그의 연결 정보를 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<Void>> removeTagFromProblem(
            @Parameter(
                    description = "태그 연결을 제거할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId,

            @Parameter(
                    description = "연결을 제거할 태그 ID",
                    required = true,
                    example = "7a9e6679-7425-40de-944b-e07fc1f90ae7"
            )
            UUID tagId
    );
}