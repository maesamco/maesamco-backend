package com.maesamco.content.tag.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
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

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final TagFinder tagFinder;

    /** 태그 생성 */
    @Transactional
    public TagCreateResponse createTag(TagCreateRequest request) {

        if (tagRepository.existsByName(request.getName())) {
            // 이름이 같은 태그는 생성하지 못하도록 한다.
            throw new BusinessException(ErrorCode.TAG_NAME_ALREADY_EXISTS);
        }

        Tag tag = Tag.create(
                request.getName(),
                request.getAttribute()
        );

        Tag savedTag = tagRepository.save(tag);

        return TagCreateResponse.from(savedTag);
    }

    /** 전체 태그 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<TagResponse> searchTags(Pageable pageable) {

        Page<Tag> tags = tagRepository.searchTags(pageable);

        return PageResponse.from(tags, TagResponse::from);
    }

    /** 특정 속성의 태그 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<TagResponse> searchTagsByAttribute(TagAttribute attribute, Pageable pageable) {

        Page<Tag> tags = tagRepository.searchTagsByAttribute(attribute, pageable);

        return PageResponse.from(tags, TagResponse::from);
    }

    /** 태그 수정 */
    @Transactional
    public void updateTag(UUID tagId, TagUpdateRequest request) {

        Tag tag = tagFinder.getTag(tagId);

        // 이름 수정 정책
        if (request.getName() != null) {
            if (tag.getName().equals(request.getName())) {
                // 현재값이랑 수정값이랑 같으면 pass
                ;
            }
            else if (tagRepository.existsByName(request.getName())) {
                // 현재값이랑 수정값이랑 다른데, 수정하려는 값이 이미 존재하면 수정하면 안된다.
                throw new BusinessException(ErrorCode.TAG_NAME_ALREADY_EXISTS);
            }
            else {
                tag.changeName(request.getName());
            }
        }

        // 속성 수정 정책
        if (request.getAttribute() != null) {
            tag.changeAttribute(request.getAttribute());
        }
    }

    /** 태그 삭제 */
    @Transactional
    public void deleteTag(UUID tagId) {

        Tag tag = tagFinder.getTag(tagId);

        tag.softDelete(tagId);
    }
}