package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.response.CurriculumResponse;
import com.maesamco.content.presentation.response.LessonResponse;
import com.maesamco.content.presentation.response.UnitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * 관리자 학습 콘텐츠(Curriculum / Unit / Lesson) 조회 및 공개 상태 전환 API(#359).
 *
 * <p>학습자 API(/api/v1/contents/...)는 본인과 상위가 모두 PUBLISHED인 콘텐츠만 반환하고,
 * 이 관리자 API는 공개 상태와 관계없이 삭제되지 않은 콘텐츠를 모두 반환합니다.</p>
 */
@Controller
@RequestMapping("/api/v1/admin/contents")
@Tag(name = "Admin Content", description = "관리자 학습 콘텐츠 조회 및 공개·비공개 전환 API")
@SecurityRequirement(name = "bearerAuth")
public interface AdminContentApiDocs {

    // ===================== Curriculum =====================

    @GetMapping("/curriculums")
    @Operation(
            summary = "관리자 커리큘럼 목록 조회",
            description = "공개 상태(DRAFT / PUBLISHED)와 관계없이 삭제되지 않은 커리큘럼 목록을 조회합니다. "
                    + "응답의 status로 공개 여부를 확인할 수 있으며 ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 커리큘럼 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    ResponseEntity<SuccessResponse<PageResponse<CurriculumResponse>>> getCurriculums(
            @Parameter(name = "page", description = "조회할 페이지 번호", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(required = false) Integer page,

            @Parameter(name = "size", description = "한 페이지에 조회할 커리큘럼 개수", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(required = false) Integer size
    );

    @GetMapping("/curriculums/{curriculumId}")
    @Operation(
            summary = "관리자 커리큘럼 단건 조회",
            description = "공개 상태와 관계없이 삭제되지 않은 커리큘럼을 조회합니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 커리큘럼 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<CurriculumResponse>> getCurriculum(
            @Parameter(description = "조회할 커리큘럼 ID")
            @PathVariable UUID curriculumId
    );

    @PatchMapping("/curriculums/{curriculumId}/publish")
    @Operation(
            summary = "커리큘럼 공개",
            description = "커리큘럼을 PUBLISHED로 바꿔 학습자 조회에 포함합니다. 이미 공개 상태면 그대로 성공합니다. "
                    + "하위 유닛·레슨의 상태는 바꾸지 않으므로, 하위도 각각 공개해야 학습자에게 보입니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "커리큘럼 공개 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<CurriculumResponse>> publishCurriculum(
            @Parameter(description = "공개할 커리큘럼 ID")
            @PathVariable UUID curriculumId
    );

    @PatchMapping("/curriculums/{curriculumId}/unpublish")
    @Operation(
            summary = "커리큘럼 비공개",
            description = "커리큘럼을 DRAFT로 바꿔 학습자 조회에서 내립니다. 이미 비공개 상태면 그대로 성공합니다. "
                    + "하위 유닛·레슨은 상태값이 그대로인 채 학습자에게 함께 숨겨지며, 다시 공개하면 원래대로 보입니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "커리큘럼 비공개 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<CurriculumResponse>> unpublishCurriculum(
            @Parameter(description = "비공개할 커리큘럼 ID")
            @PathVariable UUID curriculumId
    );

    // ===================== Unit =====================

    @GetMapping("/units")
    @Operation(
            summary = "관리자 커리큘럼별 유닛 목록 조회",
            description = "공개 상태와 관계없이 특정 커리큘럼의 삭제되지 않은 유닛 목록을 displayOrder 오름차순으로 조회합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 유닛 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 조회 대상 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<PageResponse<UnitResponse>>> getUnits(
            @Parameter(name = "curriculumId", description = "유닛 목록을 조회할 커리큘럼 ID", in = ParameterIn.QUERY)
            @RequestParam UUID curriculumId,

            @Parameter(name = "page", description = "조회할 페이지 번호", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(required = false) Integer page,

            @Parameter(name = "size", description = "한 페이지에 조회할 유닛 개수", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(required = false) Integer size
    );

    @GetMapping("/units/{unitId}")
    @Operation(
            summary = "관리자 유닛 단건 조회",
            description = "공개 상태와 관계없이 삭제되지 않은 유닛을 조회합니다. 상위 커리큘럼이 삭제됐으면 조회할 수 없습니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 유닛 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<UnitResponse>> getUnit(
            @Parameter(description = "조회할 유닛 ID")
            @PathVariable UUID unitId
    );

    @PatchMapping("/units/{unitId}/publish")
    @Operation(
            summary = "유닛 공개",
            description = "유닛을 PUBLISHED로 바꿉니다. 이미 공개 상태면 그대로 성공합니다. "
                    + "상위 커리큘럼이 비공개면 공개해도 학습자에게는 보이지 않습니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "유닛 공개 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<UnitResponse>> publishUnit(
            @Parameter(description = "공개할 유닛 ID")
            @PathVariable UUID unitId
    );

    @PatchMapping("/units/{unitId}/unpublish")
    @Operation(
            summary = "유닛 비공개",
            description = "유닛을 DRAFT로 바꿔 학습자 조회에서 내립니다. 이미 비공개 상태면 그대로 성공합니다. "
                    + "하위 레슨은 상태값이 그대로인 채 함께 숨겨집니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "유닛 비공개 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<UnitResponse>> unpublishUnit(
            @Parameter(description = "비공개할 유닛 ID")
            @PathVariable UUID unitId
    );

    // ===================== Lesson =====================

    @GetMapping("/lessons")
    @Operation(
            summary = "관리자 유닛별 레슨 목록 조회",
            description = "공개 상태와 관계없이 특정 유닛의 삭제되지 않은 레슨 목록을 displayOrder 오름차순으로 조회합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 레슨 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 조회 대상 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<PageResponse<LessonResponse>>> getLessons(
            @Parameter(name = "unitId", description = "레슨 목록을 조회할 유닛 ID", in = ParameterIn.QUERY)
            @RequestParam UUID unitId,

            @Parameter(name = "page", description = "조회할 페이지 번호", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
            @RequestParam(required = false) Integer page,

            @Parameter(name = "size", description = "한 페이지에 조회할 레슨 개수", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(required = false) Integer size
    );

    @GetMapping("/lessons/{lessonId}")
    @Operation(
            summary = "관리자 레슨 단건 조회",
            description = "공개 상태와 관계없이 삭제되지 않은 레슨을 조회합니다. 상위 유닛·커리큘럼이 삭제됐으면 조회할 수 없습니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 레슨 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "LESSON_NOT_FOUND — 레슨을 찾을 수 없음; "
                    + "UNIT_NOT_FOUND — 레슨이 속한 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<LessonResponse>> getLesson(
            @Parameter(description = "조회할 레슨 ID")
            @PathVariable UUID lessonId
    );

    @PatchMapping("/lessons/{lessonId}/publish")
    @Operation(
            summary = "레슨 공개",
            description = "레슨을 PUBLISHED로 바꿉니다. 이미 공개 상태면 그대로 성공합니다. "
                    + "상위 유닛·커리큘럼이 비공개면 공개해도 학습자에게는 보이지 않습니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "레슨 공개 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "LESSON_NOT_FOUND — 레슨을 찾을 수 없음; "
                    + "UNIT_NOT_FOUND — 레슨이 속한 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<LessonResponse>> publishLesson(
            @Parameter(description = "공개할 레슨 ID")
            @PathVariable UUID lessonId
    );

    @PatchMapping("/lessons/{lessonId}/unpublish")
    @Operation(
            summary = "레슨 비공개",
            description = "레슨을 DRAFT로 바꿔 학습자 조회에서 내립니다. 이미 비공개 상태면 그대로 성공합니다. "
                    + "레슨에 연결된 문제는 영향을 받지 않습니다(#348). ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "레슨 비공개 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "LESSON_NOT_FOUND — 레슨을 찾을 수 없음; "
                    + "UNIT_NOT_FOUND — 레슨이 속한 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<LessonResponse>> unpublishLesson(
            @Parameter(description = "비공개할 레슨 ID")
            @PathVariable UUID lessonId
    );
}
