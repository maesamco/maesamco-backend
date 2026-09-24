package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.presentation.request.TagCreateRequest;
import com.maesamco.content.presentation.request.TagUpdateRequest;
import com.maesamco.content.presentation.response.TagCreateResponse;
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

import java.util.UUID;

@Controller
@RequestMapping("/api/v1")
@Tag(name = "Tag", description = "태그 생성, 조회, 수정, 삭제 API")
public interface TagApiDocs {

    @PostMapping("/admin/contents/tags")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "태그 생성",
            description = "새로운 태그를 생성합니다. "
                    + "태그는 이름과 속성(attribute)을 기준으로 생성되며 attribute는 태그의 용도를 구분하는 값입니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "태그 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    ResponseEntity<SuccessResponse<TagCreateResponse>> createTag(
            @Valid @RequestBody TagCreateRequest request
    );

    @GetMapping("/contents/tags")
    @Operation(
            summary = "태그 목록 조회",
            description = "삭제되지 않은 태그 목록을 페이징하여 조회합니다. "
                    + "attribute를 전달하면 해당 속성의 태그만 조회하고 생략하면 전체 태그를 조회합니다. "
                    + "page와 size를 생략하면 기본 페이징 값이 적용됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "태그 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "attribute 또는 페이징 값이 올바르지 않음")
    })
    ResponseEntity<SuccessResponse<PageResponse<TagResponse>>> getTags(
            @Parameter(
                    name = "attribute",
                    description = "태그 속성 필터",
                    in = ParameterIn.QUERY,
                    schema = @Schema(
                            type = "string",
                            allowableValues = {
                                    "CONCEPT",
                                    "DIFFICULTY",
                                    "TYPE",
                                    "ETC"
                            }
                    ),
                    example = "CONCEPT"
            )
            @RequestParam(required = false) TagAttribute attribute,

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
                    description = "한 페이지에 조회할 태그 개수",
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

    @PatchMapping("/admin/contents/tags/{tagId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "태그 수정",
            description = "태그 ID를 기준으로 태그 정보를 수정합니다. "
                    + "요청에 포함된 값만 변경하며 태그 이름 또는 속성을 수정할 수 있습니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "태그 수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "TAG_NOT_FOUND — 태그를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<Void>> updateTag(
            @Parameter(description = "수정할 태그 ID")
            @PathVariable UUID tagId,
            @Valid @RequestBody TagUpdateRequest request
    );

    @DeleteMapping("/admin/contents/tags/{tagId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "태그 삭제",
            description = "태그 ID를 기준으로 태그를 삭제합니다. "
                    + "실제 데이터를 제거하지 않고 삭제 시각과 삭제 사용자 정보를 기록하는 Soft Delete 방식으로 처리합니다. "
                    + "ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "태그 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "AUTH_UNAUTHORIZED — 인증되지 않은 요청"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음"),
            @ApiResponse(responseCode = "404", description = "TAG_NOT_FOUND — 태그를 찾을 수 없음")
    })
    ResponseEntity<SuccessResponse<Void>> deleteTag(
            @Parameter(description = "삭제할 태그 ID")
            @PathVariable UUID tagId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal UUID userId
    );
}