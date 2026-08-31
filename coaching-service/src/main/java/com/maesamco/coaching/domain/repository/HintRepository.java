package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.Hint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HintRepository {

    Hint save(Hint hint);

    List<Hint> findByCoachingSessionId(UUID coachingSessionId);

    Optional<Hint> findByCoachingSessionIdAndStage(UUID coachingSessionId, int stage);
}
