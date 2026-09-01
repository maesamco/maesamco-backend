package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.AiCallHistory;

import java.util.List;
import java.util.UUID;

public interface AiCallHistoryRepository {

    AiCallHistory save(AiCallHistory aiCallHistory);

    List<AiCallHistory> findByCoachingSessionId(UUID coachingSessionId);
}
