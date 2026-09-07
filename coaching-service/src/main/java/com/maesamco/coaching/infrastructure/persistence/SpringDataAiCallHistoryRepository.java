package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataAiCallHistoryRepository extends JpaRepository<AiCallHistory, UUID> {

    List<AiCallHistory> findByCoachingSessionIdOrderByCalledAtAsc(UUID coachingSessionId);

    long countByCoachingSessionIdAndPurpose(UUID coachingSessionId, AiCallPurpose purpose);
}
