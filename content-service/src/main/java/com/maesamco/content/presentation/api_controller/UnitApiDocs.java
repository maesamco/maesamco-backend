package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.UnitCreateRequest;
import com.maesamco.content.presentation.request.UnitUpdateRequest;
import com.maesamco.content.presentation.response.UnitCreateResponse;
import com.maesamco.content.presentation.response.UnitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Unit API Swagger 문서입니다.
 *
 * <p>유닛 생성, 조회, 수정, 삭제 API에 대한 명세를 제공합니다.</p>
 *
 * <p>유닛 조회는 인증된 사용자가 사용할 수 있으며,
 * 생성, 수정, 삭제는 ADMIN 권한이 필요합니다.</p>
 */
public interface UnitApiDocs {

    @Operation(
            summary = "유닛 생성",
            description = """
                    특정 커리큘럼에 새로운 유닛을 생성합니다.

                    생성 전 상위 커리큘럼의 존재 여부를 확인합니다.
                    displayOrder는 해당 커리큘럼에 속한 기존 유닛 수를 기준으로
                    다음 순서가 자동으로 지정됩니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "유닛 생성 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            CURRICULUM_NOT_FOUND
                            - 상위 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<UnitCreateResponse>> createUnit(
            UnitCreateRequest request
    );

    @Operation(
            summary = "유닛 단건 조회",
            description = """
                    유닛 ID를 기준으로 유닛 상세 정보를 조회합니다.

                    삭제되지 않은 유닛만 조회할 수 있으며,
                    유닛이 속한 상위 커리큘럼의 유효성도 확인합니다.

                    인증된 사용자만 요청할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "유닛 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            리소스를 찾을 수 없음
                            - UNIT_NOT_FOUND: 유닛을 찾을 수 없음
                            - CURRICULUM_NOT_FOUND: 유닛이 속한 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<UnitResponse>> getUnit(
            @Parameter(
                    description = "조회할 유닛 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID unitId
    );

    @Operation(
            summary = "커리큘럼별 유닛 목록 조회",
            description = """
                    특정 커리큘럼에 속한 삭제되지 않은 유닛 목록을
                    페이징하여 조회합니다.

                    유닛은 displayOrder 기준 오름차순으로 정렬됩니다.
                    page와 size를 생략하면 기본 페이징 값이 적용됩니다.

                    인증된 사용자만 요청할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "유닛 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            CURRICULUM_NOT_FOUND
                            - 조회 대상 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<PageResponse<UnitResponse>>> getUnits(
            @Parameter(
                    name = "curriculumId",
                    description = "유닛 목록을 조회할 커리큘럼 ID",
                    required = true,
                    in = ParameterIn.QUERY,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID curriculumId,

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
                    description = "한 페이지에 조회할 유닛 개수",
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
            summary = "유닛 수정",
            description = """
                    유닛 ID를 기준으로 유닛 정보를 수정합니다.

                    수정 전 유닛과 상위 커리큘럼의 유효성을 확인하며,
                    요청에 포함된 값만 변경합니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "유닛 수정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            리소스를 찾을 수 없음
                            - UNIT_NOT_FOUND: 유닛을 찾을 수 없음
                            - CURRICULUM_NOT_FOUND: 유닛이 속한 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<UnitResponse>> updateUnit(
            @Parameter(
                    description = "수정할 유닛 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID unitId,

            UnitUpdateRequest request
    );

    @Operation(
            summary = "유닛 삭제",
            description = """
                    유닛 ID를 기준으로 유닛을 삭제합니다.

                    실제 데이터를 제거하지 않고 삭제 시각과 삭제 사용자 정보를 기록하는
                    Soft Delete 방식으로 처리합니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "유닛 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            리소스를 찾을 수 없음
                            - UNIT_NOT_FOUND: 유닛을 찾을 수 없음
                            - CURRICULUM_NOT_FOUND: 유닛이 속한 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<Void>> deleteUnit(
            @Parameter(
                    description = "삭제할 유닛 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID unitId,

            @Parameter(hidden = true)
            UUID userId
    );
}