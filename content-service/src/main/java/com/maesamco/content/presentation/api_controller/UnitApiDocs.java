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
@RequestMapping("/api/v1/contents/units")
@Tag(name = "Unit", description = "유닛 생성, 조회, 수정, 삭제 API")
@SecurityRequirement(name = "bearerAuth")
public interface UnitApiDocs {

    @PostMapping
    @Operation(
            summary = "유닛 생성",
            description = "특정 커리큘럼에 새로운 유닛을 생성합니다. "
                    + "생성 전 상위 커리큘럼의 존재 여부를 확인하며 displayOrder는 기존 유닛 수를 기준으로 자동 지정됩니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "유닛 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 상위 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<UnitCreateResponse>> createUnit(
            @Valid @RequestBody UnitCreateRequest request
    );

    @GetMapping("/{unitId}")
    @Operation(
            summary = "유닛 단건 조회",
            description = "유닛 ID를 기준으로 유닛 상세 정보를 조회합니다. "
                    + "삭제되지 않은 유닛만 조회할 수 있으며 상위 커리큘럼의 유효성도 함께 확인합니다. "
                    + "인증된 사용자만 요청할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "유닛 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<UnitResponse>> getUnit(
            @Parameter(description = "조회할 유닛 ID")
            @PathVariable UUID unitId
    );

    @GetMapping
    @Operation(
            summary = "커리큘럼별 유닛 목록 조회",
            description = "특정 커리큘럼에 속한 삭제되지 않은 유닛 목록을 페이징하여 조회합니다. "
                    + "유닛은 displayOrder 기준 오름차순으로 정렬되며 page와 size를 생략하면 기본값이 적용됩니다. "
                    + "인증된 사용자만 요청할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "유닛 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "404", description = "CURRICULUM_NOT_FOUND — 조회 대상 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<PageResponse<UnitResponse>>> getUnits(
            @Parameter(
                    name = "curriculumId",
                    description = "유닛 목록을 조회할 커리큘럼 ID",
                    in = ParameterIn.QUERY
            )
            @RequestParam UUID curriculumId,

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
                    description = "한 페이지에 조회할 유닛 개수",
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

    @PatchMapping("/{unitId}")
    @Operation(
            summary = "유닛 수정",
            description = "유닛 ID를 기준으로 유닛 정보를 수정합니다. "
                    + "수정 전 유닛과 상위 커리큘럼의 유효성을 확인하며 요청에 포함된 값만 변경합니다. "
                    + "displayOrder는 같은 커리큘럼 안에서 옮겨 갈 자리(1부터)로 해석하며, "
                    + "다른 유닛이 쓰고 있는 번호로 옮기면 사이의 유닛들이 한 칸씩 밀리거나 당겨지고 "
                    + "형제 유닛 전체가 1..N으로 다시 정렬됩니다. "
                    + "(예: [A1 B2 C3 D4 E5]에서 E를 2로 옮기면 [A1 E2 B3 C4 D5]) "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "유닛 수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패; "
                    + "UNIT_DISPLAY_ORDER_OUT_OF_RANGE — displayOrder가 1..(같은 커리큘럼의 유닛 수)를 벗어남"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<UnitResponse>> updateUnit(
            @Parameter(description = "수정할 유닛 ID")
            @PathVariable UUID unitId,
            @Valid @RequestBody UnitUpdateRequest request
    );

    @DeleteMapping("/{unitId}")
    @Operation(
            summary = "유닛 삭제",
            description = "유닛 ID를 기준으로 유닛을 삭제합니다. "
                    + "실제 데이터를 제거하지 않고 삭제 시각과 삭제 사용자 정보를 기록하는 Soft Delete 방식으로 처리합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "유닛 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "UNIT_NOT_FOUND — 유닛을 찾을 수 없음; "
                    + "CURRICULUM_NOT_FOUND — 유닛이 속한 커리큘럼을 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<Void>> deleteUnit(
            @Parameter(description = "삭제할 유닛 ID")
            @PathVariable UUID unitId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal UUID userId
    );
}