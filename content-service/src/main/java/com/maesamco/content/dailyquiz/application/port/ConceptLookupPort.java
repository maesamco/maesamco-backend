package com.maesamco.content.dailyquiz.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Content Service의 개념 ID를 Daily Quiz가 사용하는 개념 이름으로 변환하는 임시 Port입니다.
 * ConceptRepository가 병합되면 이 Port는 제거할 예정입니다.
 */
public interface ConceptLookupPort {
    List<String> getConceptNames(List<UUID> conceptIds);
}
