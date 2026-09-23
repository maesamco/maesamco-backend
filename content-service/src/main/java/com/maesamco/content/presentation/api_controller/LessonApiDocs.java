package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.LessonCreateRequest;
import com.maesamco.content.presentation.request.LessonUpdateRequest;
import com.maesamco.content.presentation.response.LessonCreateResponse;
import com.maesamco.content.presentation.response.LessonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Lesson API Swagger 문서입니다.
 *
 * <p>공통 인증 실패(401)는 전역 인증 처리에서 관리하므로
 * 각 API 응답에 반복해서 명시하지 않습니다.</p>
 */
public interface LessonApiDocs {

    @Operation(
            summary = "레슨 생성",
            description = """
                    특정 유닛에 새로운 레슨을 생성합니다.

                    레슨 생성 전 상위 유닛과 커리큘럼의 존재 여부를 검증합니다.
                    displayOrder는 해당 유닛에 속한 기존 레슨 수를 기준으로
                    다음 순서가 자동으로 지정됩니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "레슨 생성 성공"
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
                            상위 리소스를 찾을 수 없음
                            - UNIT_NOT_FOUND: 유닛을 찾을 수 없음
                            - CURRICULUM_NOT_FOUND: 유닛이 속한 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<LessonCreateResponse>> createLesson(
            LessonCreateRequest request
    );

    @Operation(
            summary = "레슨 단건 조회",
            description = """
                    레슨 ID를 기준으로 레슨 상세 정보를 조회합니다.

                    조회 과정에서 레슨과 연결된 유닛 및 커리큘럼의
                    유효성도 함께 확인합니다.
                    삭제된 리소스는 조회 대상에서 제외됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "레슨 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            리소스를 찾을 수 없음
                            - LESSON_NOT_FOUND: 레슨을 찾을 수 없음
                            - UNIT_NOT_FOUND: 레슨이 속한 유닛을 찾을 수 없음
                            - CURRICULUM_NOT_FOUND: 유닛이 속한 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<LessonResponse>> getLesson(
            @Parameter(
                    description = "조회할 레슨 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID lessonId
    );

    @Operation(
            summary = "유닛별 레슨 목록 조회",
            description = """
                    특정 유닛에 속한 삭제되지 않은 레슨 목록을 페이징하여 조회합니다.

                    레슨은 displayOrder 기준 오름차순으로 정렬됩니다.
                    page와 size를 생략하면 기본 페이징 값이 적용됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "레슨 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            상위 리소스를 찾을 수 없음
                            - UNIT_NOT_FOUND: 유닛을 찾을 수 없음
                            - CURRICULUM_NOT_FOUND: 유닛이 속한 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<PageResponse<LessonResponse>>> getLessons(
            @Parameter(
                    name = "unitId",
                    description = "레슨 목록을 조회할 유닛 ID",
                    required = true,
                    in = ParameterIn.QUERY,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID unitId,

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
            summary = "레슨 수정",
            description = """
                    레슨 ID를 기준으로 레슨 정보를 수정합니다.

                    수정 전 레슨과 연결된 유닛 및 커리큘럼의 유효성을 확인하며,
                    요청에 포함된 값만 변경합니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "레슨 수정 성공"
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
                            - LESSON_NOT_FOUND: 레슨을 찾을 수 없음
                            - UNIT_NOT_FOUND: 레슨이 속한 유닛을 찾을 수 없음
                            - CURRICULUM_NOT_FOUND: 유닛이 속한 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<LessonResponse>> updateLesson(
            @Parameter(
                    description = "수정할 레슨 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID lessonId,

            LessonUpdateRequest request
    );

    @Operation(
            summary = "레슨 삭제",
            description = """
                    레슨 ID를 기준으로 레슨을 삭제합니다.

                    실제 데이터를 제거하지 않고 삭제 시각과 삭제 사용자 정보를 기록하는
                    Soft Delete 방식으로 처리합니다.

                    ADMIN 권한이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "레슨 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            리소스를 찾을 수 없음
                            - LESSON_NOT_FOUND: 레슨을 찾을 수 없음
                            - UNIT_NOT_FOUND: 레슨이 속한 유닛을 찾을 수 없음
                            - CURRICULUM_NOT_FOUND: 유닛이 속한 커리큘럼을 찾을 수 없음
                            """
            )
    })
    ResponseEntity<SuccessResponse<Void>> deleteLesson(
            @Parameter(
                    description = "삭제할 레슨 ID",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            UUID lessonId,

            @Parameter(hidden = true)
            UUID userId
    );
}