package com.maesamco.content.tag.presentation.controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.tag.application.service.TagService;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import com.maesamco.content.tag.presentation.dto.request.TagCreateRequest;
import com.maesamco.content.tag.presentation.dto.request.TagUpdateRequest;
import com.maesamco.content.tag.presentation.dto.response.TagCreateResponse;
import com.maesamco.content.tag.presentation.dto.response.TagResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 태그 생성, 조회, 수정, 삭제를 위한 API를 제공합니다.
 *
 * <p>태그 생성, 전체 및 속성별 목록 조회,
 * 수정 및 삭제 로직은 {@link TagService}에 위임합니다.</p>
 *
 * <p>태그 조회는 모든 사용자가 이용할 수 있으며,
 * 생성, 수정 및 삭제는 ADMIN 권한을 가진 사용자만 수행할 수 있습니다.</p>
 *
 * <p>모든 정상 응답은 {@link SuccessResponse}로 감싸 반환합니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TagController {

    /** 태그 생성, 조회, 수정, 삭제 비즈니스 로직을 담당하는 서비스입니다. */
    private final TagService tagService;

    /**
     * 새로운 태그를 생성합니다.
     *
     * <p>태그 이름의 중복 여부를 확인하고,
     * 요청받은 이름과 속성을 기준으로 새로운 태그를 생성합니다.</p>
     *
     * @param request 태그 생성 요청 정보
     * @return 생성된 태그 정보를 포함한 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/tags")
    public ResponseEntity<SuccessResponse<TagCreateResponse>> createTag(
            @Valid @RequestBody TagCreateRequest request
    ) {
        TagCreateResponse response = tagService.createTag(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.success(response));
    }

    /**
     * 태그 목록을 조회합니다.
     *
     * <p>attribute가 전달되지 않으면 전체 태그를 조회하고,
     * attribute가 전달되면 해당 속성의 태그를 조회합니다.</p>
     *
     * @param attribute 조회할 태그 속성
     * @param page 조회할 페이지 번호
     * @param size 한 페이지에 조회할 태그 개수
     * @return 페이징된 태그 목록
     */
    @GetMapping("/tags")
    public ResponseEntity<SuccessResponse<PageResponse<TagResponse>>> getTags(
            @RequestParam(required = false) TagAttribute attribute,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        PageResponse<TagResponse> response = null;

        if (attribute == null) {
            response = tagService.searchTags(pageable);
        }
        else {
            response = tagService.searchTagsByAttribute(attribute, pageable);
        }

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 태그의 정보를 수정합니다.
     *
     * <p>태그 ID를 기준으로 수정 대상을 조회하고,
     * 요청에 포함된 이름 또는 속성을 수정합니다.</p>
     *
     * @param tagId 수정할 태그의 고유 ID
     * @param request 태그 수정 요청 정보
     * @return 응답 데이터가 없는 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/tags/{tagId}")
    public ResponseEntity<SuccessResponse<Void>> updateTag(
            @PathVariable UUID tagId,
            @Valid @RequestBody TagUpdateRequest request
    ) {
        tagService.updateTag(tagId, request);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }

    /**
     * 지정한 태그를 삭제합니다.
     *
     * <p>태그 ID를 기준으로 삭제 대상을 조회한 뒤
     * Soft Delete 방식으로 처리합니다.</p>
     *
     * @param tagId 삭제할 태그의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/tags/{tagId}")
    public ResponseEntity<SuccessResponse<Void>> deleteTag(
            @PathVariable UUID tagId
    ) {
        tagService.deleteTag(tagId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}