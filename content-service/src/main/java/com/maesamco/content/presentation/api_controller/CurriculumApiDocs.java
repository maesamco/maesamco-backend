package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.CurriculumCreateRequest;
import com.maesamco.content.presentation.request.CurriculumUpdateRequest;
import com.maesamco.content.presentation.response.CurriculumCreateResponse;
import com.maesamco.content.presentation.response.CurriculumResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Curriculum API Swagger 문서입니다.
 *
 * <p>공통 인증 실패(401)는 전역 인증 처리에서 관리하므로
 * 각 API 응답에 반복해서 명시하지 않습니다.</p>
 */
public interface CurriculumApiDocs {

    @Operation(
            summary = "커리큘럼 생성",
            description = """
                    새로운 커리큘럼을 생성합니다.
                    
                    생성된 커리큘럼의 displayOrder는 현재 커리큘럼 수를 기준으로
                    다음 순서가 자동으로 지정됩니다.
                    
                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "커리큘럼 생성 성공"
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
    ResponseEntity<SuccessResponse<CurriculumCreateResponse>> createCurriculum(
            CurriculumCreateRequest request
    );

    @Operation(
            summary = "커리큘럼 단건 조회",
            description = """
                    커리큘럼 ID를 기준으로 커리큘럼을 조회합니다.
                    
                    삭제되지 않은 커리큘럼만 조회할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "커리큘럼 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "CURRICULUM_NOT_FOUND - 커리큘럼을 찾을 수 없음"
            )
    })
    ResponseEntity<SuccessResponse<CurriculumResponse>> getCurriculum(
            @Parameter(
                    description = "조회할 커리큘럼 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID curriculumId
    );

    @Operation(
            summary = "커리큘럼 목록 조회",
            description = """
                    삭제되지 않은 커리큘럼 목록을 페이징하여 조회합니다.
                    
                    커리큘럼은 displayOrder 기준 오름차순으로 정렬됩니다.
                    page와 size를 생략하면 기본 페이징 값이 적용됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "커리큘럼 목록 조회 성공"
            )
    })
    ResponseEntity<SuccessResponse<PageResponse<CurriculumResponse>>> getCurriculums(
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
            Integer size
    );

    @Operation(
            summary = "커리큘럼 수정",
            description = """
                    커리큘럼 ID를 기준으로 커리큘럼 정보를 수정합니다.
                    
                    요청에 포함된 값만 변경하며,
                    displayOrder는 수정 대상에 포함되지 않습니다.
                    
                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "커리큘럼 수정 성공"
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
                    description = "CURRICULUM_NOT_FOUND - 커리큘럼을 찾을 수 없음"
            )
    })
    ResponseEntity<SuccessResponse<CurriculumResponse>> updateCurriculum(
            @Parameter(
                    description = "수정할 커리큘럼 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID curriculumId,

            CurriculumUpdateRequest request
    );

    @Operation(
            summary = "커리큘럼 삭제",
            description = """
                    커리큘럼 ID를 기준으로 커리큘럼을 삭제합니다.
                    
                    실제 데이터를 제거하지 않고 삭제 시각과 삭제 사용자 정보를 기록하는
                    Soft Delete 방식으로 처리합니다.
                    
                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "커리큘럼 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "CURRICULUM_NOT_FOUND - 커리큘럼을 찾을 수 없음"
            )
    })
    ResponseEntity<SuccessResponse<Void>> deleteCurriculum(
            @Parameter(
                    description = "삭제할 커리큘럼 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID curriculumId,

            @Parameter(hidden = true)
            UUID userId
    );
}