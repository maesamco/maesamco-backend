package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;

import java.util.List;
import java.util.UUID;

public interface AiCallHistoryRepository {

    AiCallHistory save(AiCallHistory aiCallHistory);

    List<AiCallHistory> findByCoachingSessionIdOrderByCalledAtAsc(UUID coachingSessionId);

    /**
     * 이슈 #52 — 세션당 재시도 상한(3회) 계산용. "SKIPPED"(서킷브레이커 OPEN 등으로 실제
     * LLM 호출 자체가 없었던 시도)와 "INFRA_FAILED"(호출은 했지만 네트워크·타임아웃 등
     * 인프라 사정으로 실패한 시도, PR #182 리뷰·용현님 P2로 SKIPPED에서 분리)는 둘 다
     * 제외한다 — 토큰이 청구되지 않았거나 우리 쪽 사정과 무관한 실패를 재시도 횟수로
     * 세면 안 되기 때문이다.
     */
    long countRealAttemptsByCoachingSessionIdAndPurpose(UUID coachingSessionId, AiCallPurpose purpose);
}
