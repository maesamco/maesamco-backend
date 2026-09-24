package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.TagService;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.presentation.request.TagCreateRequest;
import com.maesamco.content.presentation.request.TagUpdateRequest;
import com.maesamco.content.presentation.response.TagCreateResponse;
import com.maesamco.content.presentation.response.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.maesamco.content.global.security.authorization.RequireAdmin;
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
public class TagController implements TagApiDocs {

    private final TagService tagService;

    /**
     * 태그를 생성합니다.
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<TagCreateResponse>> createTag(
            TagCreateRequest request
    ) {
        TagCreateResponse response = tagService.createTag(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        SuccessResponse.success(response)
                );
    }

    /**
     * 태그 목록을 조회합니다.
     */
    @Override
    public ResponseEntity<SuccessResponse<PageResponse<TagResponse>>> getTags(
            TagAttribute attribute,
            Integer page,
            Integer size
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        PageResponse<TagResponse> response;

        if (attribute == null) {
            response = tagService.searchTags(pageable);
        } else {
            response = tagService.searchTagsByAttribute(attribute, pageable);
        }

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 태그 정보를 수정합니다.
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<Void>> updateTag(UUID tagId, TagUpdateRequest request) {
        tagService.updateTag(tagId, request);

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
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<Void>> deleteTag(UUID tagId, UUID userId) {
        tagService.deleteTag(tagId, userId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}