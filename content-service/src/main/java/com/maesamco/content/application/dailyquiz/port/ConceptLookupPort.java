package com.maesamco.content.application.dailyquiz.port;

import java.util.List;
import java.util.UUID;

/**
 * User Service에서 조회한 관심 개념 태그 ID를
 * Daily Quiz가 사용하는 개념 태그명으로 변환
 */
public interface ConceptLookupPort {
    List<String> getConceptTags(List<UUID> tagIds);
}
