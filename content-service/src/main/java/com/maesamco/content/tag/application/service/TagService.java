package com.maesamco.content.tag.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.problem.domain.repository.ProblemTagRepository;
import com.maesamco.content.tag.application.port.TagFinder;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import com.maesamco.content.tag.domain.repository.TagRepository;
import com.maesamco.content.tag.presentation.dto.request.TagCreateRequest;
import com.maesamco.content.tag.presentation.dto.request.TagUpdateRequest;
import com.maesamco.content.tag.presentation.dto.response.TagCreateResponse;
import com.maesamco.content.tag.presentation.dto.response.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 태그 생성, 조회, 수정, 삭제를 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final ProblemTagRepository problemTagRepository;
    private final TagFinder tagFinder;

    /**
     * 태그를 생성합니다.
     */
    @Transactional
    public TagCreateResponse createTag(
            TagCreateRequest request
    ) {
        if (tagRepository.existsByName(request.getName())) {
            throw new BusinessException(
                    ErrorCode.TAG_NAME_ALREADY_EXISTS
            );
        }

        Tag tag = Tag.create(
                request.getName(),
                request.getAttribute()
        );

        Tag savedTag =
                tagRepository.save(tag);

        return TagCreateResponse.from(savedTag);
    }

    /**
     * 전체 태그 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public PageResponse<TagResponse> searchTags(
            Pageable pageable
    ) {
        Page<Tag> tags =
                tagRepository.searchTags(pageable);

        return PageResponse.from(
                tags,
                TagResponse::from
        );
    }

    /**
     * 특정 속성의 태그 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public PageResponse<TagResponse> searchTagsByAttribute(
            TagAttribute attribute,
            Pageable pageable
    ) {
        Page<Tag> tags =
                tagRepository.searchTagsByAttribute(
                        attribute,
                        pageable
                );

        return PageResponse.from(
                tags,
                TagResponse::from
        );
    }

    /**
     * 태그 정보를 수정합니다.
     */
    @Transactional
    public void updateTag(
            UUID tagId,
            TagUpdateRequest request
    ) {
        Tag tag =
                tagFinder.getTag(tagId);

        if (request.getName() != null
                && !tag.getName().equals(request.getName())) {

            if (tagRepository.existsByName(request.getName())) {
                throw new BusinessException(
                        ErrorCode.TAG_NAME_ALREADY_EXISTS
                );
            }

            tag.changeName(request.getName());
        }

        if (request.getAttribute() != null) {
            tag.changeAttribute(
                    request.getAttribute()
            );
        }
    }

    /**
     * 태그를 삭제합니다.
     *
     * <p>삭제되는 태그를 참조하는 ProblemTag 연결을 먼저
     * Hard Delete하고, 태그에는 실제 요청 사용자 ID를
     * deletedBy로 기록하여 Soft Delete합니다.</p>
     *
     * @param tagId 삭제할 태그 식별자
     * @param userId 삭제를 요청한 사용자 식별자
     */
    @Transactional
    public void deleteTag(
            UUID tagId,
            UUID userId
    ) {
        Tag tag =
                tagFinder.getTag(tagId);

        problemTagRepository.deleteAllByTagId(tagId);

        tag.softDelete(userId);
    }
}
