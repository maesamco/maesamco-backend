package com.maesamco.coaching.application.query_service;

import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.domain.repository.WeakConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 취약 개념 조회(코칭 서비스 API 명세 7·8번 API) — 단순 조회 하나뿐이라 Facade가 아니라
 * QueryService(팀 컨벤션 2절). 사용자 본인용(`WeakConceptApiController`)과 Content Service
 * 내부용(`WeakConceptInternalController`)이 소유권 검증 여부만 다를 뿐 조회 자체는 동일해서
 * 이 하나로 같이 쓴다 — 내부 API는 호출하는 서비스가 이미 유효한 userId를 넘긴다고
 * 가정하므로(코칭 서비스 API 명세 8번 API) 여기서 별도 소유권 검증을 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class WeakConceptQueryService {

    private final WeakConceptRepository weakConceptRepository;

    public List<WeakConcept> getWeakConcepts(UUID userId) {
        return weakConceptRepository.findByUserIdOrderByImprovedAscOccurrenceCountDesc(userId);
    }
}
