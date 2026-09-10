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
 *
 * **페이징/limit 없음(PR #124 리뷰, 용현님 — 확인 후 유지로 결정)**: WeakConcept는 AI가
 * 새로 반환하는 conceptTag마다 계속 누적될 수 있는 집계 데이터라 상한이 없는 건 사실이지만,
 * 실제로는 문제 세트에 등장하는 개념 태그 어휘 자체가 유한하고 그 수가 작아(교육 과정
 * 단위 개념) 사용자 한 명의 행 수가 무한정 커질 가능성은 낮다고 판단해 지금은 전체 조회를
 * 유지한다. 나중에 개념 태그 종류가 크게 늘거나 실제로 응답이 커지는 게 확인되면 limit/
 * pagination 도입을 재검토한다.
 */
@Service
@RequiredArgsConstructor
public class WeakConceptQueryService {

    private final WeakConceptRepository weakConceptRepository;

    public List<WeakConcept> getWeakConcepts(UUID userId) {
        return weakConceptRepository.findByUserIdOrderByImprovedAscOccurrenceCountDescLastDetectedAtDesc(userId);
    }
}
