package com.maesamco.content.tag.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.tag.application.port.TagFinder;
import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagFinderService implements TagFinder {

    private final TagRepository tagRepository;

    /** 태그 단건 조회 */
    @Override
    @Transactional(readOnly = true)
    public Tag getTag(UUID tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.TAG_NOT_FOUND)
                );
    }
}