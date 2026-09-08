package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;

import java.util.List;
import java.util.UUID;

public interface AiCallHistoryRepository {

    AiCallHistory save(AiCallHistory aiCallHistory);

    List<AiCallHistory> findByCoachingSessionIdOrderByCalledAtAsc(UUID coachingSessionId);

    /**
     * 이슈 #52 — 세션당 재시도 상한(3회) 계산용. "SKIPPED"(서킷브레이커 OPEN으로 실제 LLM
     * 호출 자체가 없었던 시도, PR #111 재검증)는 제외한다 — 실제로 시도하지 않은 걸
     * 재시도 횟수로 세면 안 되기 때문이다.
     */
    long countRealAttemptsByCoachingSessionIdAndPurpose(UUID coachingSessionId, AiCallPurpose purpose);
}
