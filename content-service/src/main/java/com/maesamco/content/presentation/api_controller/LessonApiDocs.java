package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.LessonCreateRequest;
import com.maesamco.content.presentation.request.LessonUpdateRequest;
import com.maesamco.content.presentation.response.LessonCreateResponse;
import com.maesamco.content.presentation.response.LessonResponse;
import com.maesamco.content.presentation.response.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/api/v1/contents/lessons")
@Tag(name = "Lesson", description = "레슨 생성, 조회, 수정, 삭제 및 개념 조회 API")
@SecurityRequirement(name = "bearerAuth")
public interface LessonApiDocs {

    @PostMapping
    @Operation(
            summary = "레슨 생성",
            description = "특정 유닛에 새로운 레슨을 생성합니다. "
                    + "생성 전 상위 유닛과 커리큘럼의 존재 여부를 검증하며, displayOrder는 기존 레슨 수를 기준으로 자동 지정됩니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "레슨 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<LessonCreateResponse>> createLesson(
            @Valid @RequestBody LessonCreateRequest request
    );

    @GetMapping("/{lessonId}")
    @Operation(
            summary = "레슨 단건 조회",
            description = "레슨 ID를 기준으로 레슨 상세 정보를 조회합니다. "
                    + "조회 과정에서 연결된 유닛 및 커리큘럼의 유효성을 함께 확인합니다. "
                    + "삭제된 리소스는 조회 대상에서 제외됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "레슨 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "LESSON_NOT_FOUND — 레슨을 찾을 수 없음; "
                    + "UNIT_NOT_FOUND — 레슨이 속한 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<LessonResponse>> getLesson(
            @Parameter(description = "조회할 레슨 ID")
            @PathVariable UUID lessonId
    );

    @GetMapping
    @Operation(
            summary = "유닛별 레슨 목록 조회",
            description = "특정 유닛에 속한 삭제되지 않은 레슨 목록을 페이징하여 조회합니다. "
                    + "레슨은 displayOrder 기준 오름차순으로 정렬됩니다. "
                    + "page와 size를 생략하면 기본 페이징 값이 적용됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "레슨 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<PageResponse<LessonResponse>>> getLessons(
            @Parameter(
                    name = "unitId",
                    description = "레슨 목록을 조회할 유닛 ID",
                    in = ParameterIn.QUERY
            )
            @RequestParam UUID unitId,

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
                    description = "한 페이지에 조회할 데이터 개수",
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

    @PatchMapping("/{lessonId}")
    @Operation(
            summary = "레슨 수정",
            description = "레슨 ID를 기준으로 레슨 정보를 수정합니다. "
                    + "수정 전 연결된 유닛 및 커리큘럼의 유효성을 확인하며 요청에 포함된 값만 변경합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "레슨 수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "LESSON_NOT_FOUND — 레슨을 찾을 수 없음; "
                    + "UNIT_NOT_FOUND — 레슨이 속한 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<LessonResponse>> updateLesson(
            @Parameter(description = "수정할 레슨 ID")
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonUpdateRequest request
    );

    @DeleteMapping("/{lessonId}")
    @Operation(
            summary = "레슨 삭제",
            description = "레슨 ID를 기준으로 레슨을 삭제합니다. "
                    + "실제 데이터를 제거하지 않고 삭제 시각과 삭제 사용자 정보를 기록하는 Soft Delete 방식으로 처리합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "레슨 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "LESSON_NOT_FOUND — 레슨을 찾을 수 없음; "
                    + "UNIT_NOT_FOUND — 레슨이 속한 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<Void>> deleteLesson(
            @Parameter(description = "삭제할 레슨 ID")
            @PathVariable UUID lessonId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal UUID userId
    );

    @GetMapping("/{lessonId}/concepts")
    @Operation(
            summary = "레슨 개념 목록 조회",
            description = "레슨에 연결된 문제들의 CONCEPT 태그를 중복 없이 조회합니다. "
                    + "레슨이 태그를 직접 소유하는 것이 아니라 연결된 문제를 기준으로 계산되는 파생값입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "레슨 개념 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "LESSON_NOT_FOUND — 레슨을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<List<TagResponse>>> getLessonConcepts(
            @Parameter(description = "개념 목록을 조회할 레슨 ID")
            @PathVariable UUID lessonId
    );
}