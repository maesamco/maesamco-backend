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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/**
 * 태그 생성, 조회, 수정, 삭제 API를 제공합니다.
 *
 * <p>태그 조회는 공개하고, 생성·수정·삭제는
 * ADMIN 권한을 가진 사용자만 수행할 수 있습니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TagController {

    private final TagService tagService;

    /**
     * 태그를 생성합니다.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/tags")
    public ResponseEntity<
            SuccessResponse<TagCreateResponse>
            > createTag(
            @Valid @RequestBody TagCreateRequest request
    ) {
        TagCreateResponse response =
                tagService.createTag(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        SuccessResponse.success(response)
                );
    }

    /**
     * 태그 목록을 조회합니다.
     */
    @GetMapping("/tags")
    public ResponseEntity<
            SuccessResponse<PageResponse<TagResponse>>
            > getTags(
            @RequestParam(required = false)
            TagAttribute attribute,

            @RequestParam(required = false)
            Integer page,

            @RequestParam(required = false)
            Integer size
    ) {
        Pageable pageable =
                PageableFactory.of(
                        page,
                        size,
                        null,
                        null
                );

        PageResponse<TagResponse> response;

        if (attribute == null) {
            response =
                    tagService.searchTags(pageable);
        } else {
            response =
                    tagService.searchTagsByAttribute(
                            attribute,
                            pageable
                    );
        }

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 태그 정보를 수정합니다.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/tags/{tagId}")
    public ResponseEntity<SuccessResponse<Void>> updateTag(
            @PathVariable UUID tagId,
            @Valid @RequestBody TagUpdateRequest request
    ) {
        tagService.updateTag(
                tagId,
                request
        );

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }

    /**
     * 태그를 삭제합니다.
     *
     * @param tagId 삭제할 태그 식별자
     * @param userId 인증된 관리자 식별자
     * @return 데이터가 없는 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/tags/{tagId}")
    public ResponseEntity<SuccessResponse<Void>> deleteTag(
            @PathVariable UUID tagId,
            @AuthenticationPrincipal UUID userId
    ) {
        tagService.deleteTag(
                tagId,
                userId
        );

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}
