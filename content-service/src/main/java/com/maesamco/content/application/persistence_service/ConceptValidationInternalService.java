package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.result.ConceptValidationInternalResult;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 내부 서비스용 개념(Concept) 일괄 검증 서비스(이슈 #309).
 *
 * <p>"개념"은 content-service에서 별도 도메인이 아니라 {@code attribute == CONCEPT}인
 * {@link Tag}로 표현된다(이슈 #291, {@code LessonController.getLessonConcepts()}와 동일한
 * 전제). 삭제되거나 다른 attribute(자료구조/알고리즘/기타)인 태그 ID는 무효로 취급한다.</p>
 */
@Service
@RequiredArgsConstructor
public class ConceptValidationInternalService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public ConceptValidationInternalResult validate(List<UUID> conceptIds) {
        List<UUID> distinctConceptIds = conceptIds.stream().distinct().toList();

        Set<UUID> foundConceptIds = tagRepository.findAllByIds(distinctConceptIds).stream()
                .filter(tag -> tag.getAttribute() == TagAttribute.CONCEPT)
                .map(Tag::getId)
                .collect(Collectors.toSet());

        List<UUID> validConceptIds = distinctConceptIds.stream()
                .filter(foundConceptIds::contains)
                .toList();
        List<UUID> invalidConceptIds = distinctConceptIds.stream()
                .filter(id -> !foundConceptIds.contains(id))
                .toList();

        return new ConceptValidationInternalResult(
                invalidConceptIds.isEmpty(),
                validConceptIds,
                invalidConceptIds
        );
    }
}
