package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.AiFeedback;

import java.util.Optional;
import java.util.UUID;

public interface AiFeedbackRepository {

    AiFeedback save(AiFeedback aiFeedback);

    Optional<AiFeedback> findByCoachingSessionId(UUID coachingSessionId);
}
