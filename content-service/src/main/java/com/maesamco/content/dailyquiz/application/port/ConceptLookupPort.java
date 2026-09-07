package com.maesamco.content.dailyquiz.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Content Service의 개념 ID를 Daily Quiz가 사용하는 개념 이름으로 변환하는 포트
 * 정식 문제 담당자의 Concept 구현이 반영되면 실제 Repository 조회와 연결합니다.
 */
public interface ConceptLookupPort {
    List<String> getConceptNames(List<UUID> conceptIds);
}
