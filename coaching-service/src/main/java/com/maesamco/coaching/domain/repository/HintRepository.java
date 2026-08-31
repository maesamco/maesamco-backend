package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.Hint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HintRepository {

    Hint save(Hint hint);

    /** 힌트는 단계 순서대로 제공되는 데이터라 stage 오름차순으로 정렬해서 반환한다. */
    List<Hint> findByCoachingSessionId(UUID coachingSessionId);

    Optional<Hint> findByCoachingSessionIdAndStage(UUID coachingSessionId, int stage);
}
