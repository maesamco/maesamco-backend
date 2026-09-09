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
     *
     * {@code improved}/{@code occurrenceCount}가 둘 다 같은 행이 여러 개 있으면 그 사이의
     * 상대 순서를 DB가 보장하지 않아서(PR #124 리뷰, 용현님) {@code lastDetectedAt} 내림차순을
     * 최종 tie-breaker로 추가했다 — 같은 요청을 반복해도 동순위 항목의 순서가 흔들리지 않는다.
     */
    List<WeakConcept> findByUserIdOrderByImprovedAscOccurrenceCountDescLastDetectedAtDesc(UUID userId);
}
