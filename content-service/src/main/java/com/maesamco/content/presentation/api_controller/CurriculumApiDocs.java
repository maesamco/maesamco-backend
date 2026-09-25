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

import java.util.UUID;

@Controller
@RequestMapping("/api/v1/contents/curriculums")
@Tag(name = "Curriculum", description = "커리큘럼 생성, 조회, 수정, 삭제 API")
@SecurityRequirement(name = "bearerAuth")
public interface CurriculumApiDocs {

    @PostMapping
    @Operation(
            summary = "커리큘럼 생성",
            description = "새로운 커리큘럼을 생성합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "커리큘럼 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    ResponseEntity<SuccessResponse<CurriculumCreateResponse>> createCurriculum(
            @Valid @RequestBody CurriculumCreateRequest request
    );

    @GetMapping("/{curriculumId}")
    @Operation(
            summary = "커리큘럼 단건 조회",
            description = "커리큘럼 ID를 기준으로 커리큘럼을 조회합니다. "
                    + "삭제되지 않은 커리큘럼만 조회할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "커리큘럼 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<CurriculumResponse>> getCurriculum(
            @Parameter(description = "조회할 커리큘럼 ID")
            @PathVariable UUID curriculumId
    );

    @GetMapping
    @Operation(
            summary = "커리큘럼 목록 조회",
            description = "삭제되지 않은 커리큘럼 목록을 페이징하여 조회합니다. "
                    + "커리큘럼은 ID 기준 오름차순으로 정렬됩니다. "
                    + "page와 size를 생략하면 기본 페이징 값이 적용됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "커리큘럼 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청")
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

    @PatchMapping("/{curriculumId}")
    @Operation(
            summary = "커리큘럼 수정",
            description = "커리큘럼 ID를 기준으로 커리큘럼 정보를 수정합니다. "
                    + "요청에 포함된 값만 변경합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "커리큘럼 수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<CurriculumResponse>> updateCurriculum(
            @Parameter(description = "수정할 커리큘럼 ID")
            @PathVariable UUID curriculumId,
            @Valid @RequestBody CurriculumUpdateRequest request
    );

    @DeleteMapping("/{curriculumId}")
    @Operation(
            summary = "커리큘럼 삭제",
            description = "커리큘럼 ID를 기준으로 커리큘럼을 삭제합니다. "
                    + "실제 데이터를 제거하지 않고 삭제 시각과 삭제 사용자 정보를 기록하는 Soft Delete 방식으로 처리합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "커리큘럼 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<Void>> deleteCurriculum(
            @Parameter(description = "삭제할 커리큘럼 ID")
            @PathVariable UUID curriculumId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal UUID userId
    );
}