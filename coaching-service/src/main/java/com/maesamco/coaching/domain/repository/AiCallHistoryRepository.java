package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;

import java.util.List;
import java.util.UUID;

public interface AiCallHistoryRepository {

    AiCallHistory save(AiCallHistory aiCallHistory);

    List<AiCallHistory> findByCoachingSessionIdOrderByCalledAtAsc(UUID coachingSessionId);

    /** 이슈 #52 — 세션당 재시도 상한(3회) 계산용. */
    long countByCoachingSessionIdAndPurpose(UUID coachingSessionId, AiCallPurpose purpose);
}
