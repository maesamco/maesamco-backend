package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.WeakConcept;

import java.util.Optional;
import java.util.UUID;

public interface WeakConceptRepository {

    WeakConcept save(WeakConcept weakConcept);

    Optional<WeakConcept> findByUserIdAndConceptTag(UUID userId, String conceptTag);
}
