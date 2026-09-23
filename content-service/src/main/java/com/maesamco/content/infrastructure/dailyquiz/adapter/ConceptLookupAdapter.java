package com.maesamco.content.infrastructure.dailyquiz.adapter;

import com.maesamco.content.application.dailyquiz.port.ConceptLookupPort;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.TagRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관심 개념 태그 ID를 활성 CONCEPT 태그명으로 변환하는 Adapter
 */
@Component
@RequiredArgsConstructor
public class ConceptLookupAdapter implements ConceptLookupPort {

    private final TagRepository tagRepository;

    @Override
    public List<String> getConceptTags(List<UUID> tagIds) {
        if (tagIds == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "관심 개념 태그 ID 목록은 필수입니다."
            );
        }
        if (tagIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "관심 개념 태그 ID는 비어 있을 수 없습니다."
            );
        }
        if (tagIds.isEmpty()) {
            return List.of();
        }

        List<UUID> distinctTagIds = tagIds.stream()
                .distinct()
                .toList();
        Map<UUID, Tag> tagsById = tagRepository.findAllByIds(distinctTagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));

        return distinctTagIds.stream()
                .map(tagsById::get)
                .filter(tag -> tag != null)
                .filter(tag -> tag.getAttribute() == TagAttribute.CONCEPT)
                .map(Tag::getName)
                .toList();
    }
}
