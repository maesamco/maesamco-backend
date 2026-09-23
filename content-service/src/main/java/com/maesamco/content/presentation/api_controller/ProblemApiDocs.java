package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.ProblemCreateRequest;
import com.maesamco.content.presentation.request.ProblemSearchRequest;
import com.maesamco.content.presentation.request.ProblemUpdateRequest;
import com.maesamco.content.presentation.response.ProblemCreateResponse;
import com.maesamco.content.presentation.response.ProblemResponse;
import com.maesamco.content.presentation.response.ProblemSearchItemResponse;
import com.maesamco.content.presentation.response.ProblemShortResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Problem API Swagger 문서입니다.
 *
 * <p>공통 인증 실패(401)는 전역 인증 처리에서 관리하므로
 * 각 API 응답에 반복해서 명시하지 않습니다.</p>
 */
public interface ProblemApiDocs {

    @Operation(
            summary = "문제 생성",
            description = """
                    새로운 문제를 생성합니다.

                    문제는 최초 생성 시 발행 전 상태로 생성되며,
                    이후 별도의 검증 및 발행 승인 절차를 거쳐 사용자에게 공개됩니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "문제 생성 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            )
    })
    ResponseEntity<SuccessResponse<ProblemCreateResponse>> createProblem(
            ProblemCreateRequest request
    );

    @Operation(
            summary = "관리자용 문제 단건 조회",
            description = """
                    문제 ID를 기준으로 문제의 전체 정보를 조회합니다.

                    문제의 발행 상태와 관계없이 관리에 필요한 상세 정보를 조회할 수 있습니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "PROBLEM_NOT_FOUND - 문제를 찾을 수 없음"
            )
    })
    ResponseEntity<SuccessResponse<ProblemResponse>> getProblem(
            @Parameter(
                    description = "조회할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId
    );

    @Operation(
            summary = "사용자용 문제 단건 조회",
            description = """
                    문제 ID를 기준으로 사용자에게 공개할 문제 정보를 조회합니다.

                    PUBLISHED 상태의 문제만 조회할 수 있으며,
                    아직 발행되지 않은 문제는 사용자에게 제공되지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            문제를 조회할 수 없음
                            - PROBLEM_NOT_FOUND: 문제를 찾을 수 없음
                            - PROBLEM_NOT_PUBLISHED: 아직 발행되지 않은 문제
                            """
            )
    })
    ResponseEntity<SuccessResponse<ProblemShortResponse>> getProblemShort(
            @Parameter(
                    description = "조회할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId
    );

    @Operation(
            summary = "문제 목록 검색",
            description = """
                    검색 조건에 해당하는 문제 목록을 페이징하여 조회합니다.

                    언어, 난이도, 문제 유형, 출처 등의 조건을 조합하여 검색할 수 있습니다.
                    사용자에게 제공되는 목록은 PUBLISHED 상태의 문제로 제한됩니다.

                    page와 size를 생략하면 기본 페이징 값이 적용됩니다.
                    sort와 direction을 통해 정렬 조건을 전달할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "검색 조건 또는 페이징/정렬 값이 올바르지 않음"
            )
    })
    ResponseEntity<SuccessResponse<PageResponse<ProblemSearchItemResponse>>> getProblems(
            @ParameterObject
            ProblemSearchRequest request,

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
                    description = "한 페이지에 조회할 데이터 개수",
                    in = ParameterIn.QUERY,
                    schema = @Schema(
                            type = "integer",
                            defaultValue = "20",
                            minimum = "1",
                            maximum = "100"
                    )
            )
            Integer size,

            @Parameter(
                    name = "sort",
                    description = "정렬 기준으로 사용할 필드명",
                    in = ParameterIn.QUERY,
                    example = "createdAt"
            )
            String sort,

            @Parameter(
                    name = "direction",
                    description = "정렬 방향",
                    in = ParameterIn.QUERY,
                    schema = @Schema(
                            type = "string",
                            allowableValues = {"asc", "desc"}
                    ),
                    example = "desc"
            )
            String direction
    );

    @Operation(
            summary = "문제 수정",
            description = """
                    문제 ID를 기준으로 문제 정보를 수정합니다.

                    요청에 포함된 값만 변경하며,
                    수정 시 문제의 현재 버전 번호가 증가합니다.

                    동시 수정으로 인한 데이터 충돌을 방지하기 위해
                    문제를 잠근 상태에서 버전 정보를 검증합니다.
                    요청 시점의 버전과 현재 버전이 일치하지 않으면
                    PROBLEM_MODIFIED_CONCURRENTLY 오류가 발생합니다.

                    starterCode가 필요한 상태에서 초기화되어 있지 않은 경우
                    STARTER_CODE_NOT_INITIALIZED 오류가 발생할 수 있습니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 수정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            요청 값 검증 실패
                            - STARTER_CODE_NOT_INITIALIZED: starterCode가 초기화되지 않음
                            """
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "PROBLEM_NOT_FOUND - 문제를 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            PROBLEM_MODIFIED_CONCURRENTLY
                            - 다른 요청에 의해 문제가 먼저 수정되어 버전 충돌이 발생함
                            """
            )
    })
    ResponseEntity<SuccessResponse<ProblemResponse>> updateProblem(
            @Parameter(
                    description = "수정할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId,

            ProblemUpdateRequest request
    );

    @Operation(
            summary = "문제 삭제",
            description = """
                    문제 ID를 기준으로 문제를 삭제합니다.

                    실제 데이터를 제거하지 않고 삭제 시각과 삭제 사용자 정보를 기록하는
                    Soft Delete 방식으로 처리합니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "문제 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "PROBLEM_NOT_FOUND - 문제를 찾을 수 없음"
            )
    })
    ResponseEntity<SuccessResponse<Void>> deleteProblem(
            @Parameter(
                    description = "삭제할 문제 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID problemId,

            @Parameter(hidden = true)
            UUID userId
    );
}