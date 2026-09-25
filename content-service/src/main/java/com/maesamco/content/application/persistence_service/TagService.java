package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.TagFinder;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.TagRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.application.command.TagCreateCommand;
import com.maesamco.content.application.command.TagUpdateCommand;
import com.maesamco.content.application.result.TagResult;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import lombok.RequiredArgsConstructor;
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
    public TagResult createTag(
            TagCreateCommand command
    ) {
        if (tagRepository.existsByName(command.getName())) {
            throw new BusinessException(
                    ErrorCode.TAG_NAME_ALREADY_EXISTS
            );
        }

        Tag tag = Tag.create(
                command.getName(),
                command.getAttribute()
        );

        Tag savedTag =
                tagRepository.save(tag);

        return TagResult.from(savedTag);
    }

    /**
     * 전체 태그 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public PageResult<TagResult> searchTags(
            PageQuery pageQuery
    ) {
        PageResult<Tag> tags =
                tagRepository.searchTags(pageQuery);

        return tags.map(
                TagResult::from
        );
    }

    /**
     * 특정 속성의 태그 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public PageResult<TagResult> searchTagsByAttribute(
            TagAttribute attribute,
            PageQuery pageQuery
    ) {
        PageResult<Tag> tags =
                tagRepository.searchTagsByAttribute(
                        attribute,
                        pageQuery
                );

        return tags.map(
                TagResult::from
        );
    }

    /**
     * 태그 정보를 수정합니다.
     */
    @Transactional
    public void updateTag(
            UUID tagId,
            TagUpdateCommand command
    ) {
        Tag tag =
                tagFinder.getById(tagId);

        if (command.getName() != null
                && !tag.getName().equals(command.getName())) {

            if (tagRepository.existsByName(command.getName())) {
                throw new BusinessException(
                        ErrorCode.TAG_NAME_ALREADY_EXISTS
                );
            }

            tag.changeName(command.getName());
        }

        if (command.getAttribute() != null) {
            tag.changeAttribute(
                    command.getAttribute()
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
                tagFinder.getById(tagId);

        problemTagRepository.deleteAllByTagId(tagId);

        tag.softDelete(userId);
    }
}
