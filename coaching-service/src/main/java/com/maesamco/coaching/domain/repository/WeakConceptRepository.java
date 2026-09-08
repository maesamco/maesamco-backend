package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.WeakConcept;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeakConceptRepository {

    WeakConcept save(WeakConcept weakConcept);

    Optional<WeakConcept> findByUserIdAndConceptTag(UUID userId, String conceptTag);

    /**
     * 이슈 #54 — 사용자의 취약 개념 목록을 우선순위(코칭 서비스 API 명세 7번 API — 개선
     * 안 된 것 우선, 그다음 발견 횟수 높은 순) 그대로 조회한다.
     */
    List<WeakConcept> findByUserIdOrderByImprovedAscOccurrenceCountDesc(UUID userId);
}
