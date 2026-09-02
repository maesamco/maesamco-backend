package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakConcept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataWeakConceptRepository extends JpaRepository<WeakConcept, UUID> {

    Optional<WeakConcept> findByUserIdAndConceptTag(UUID userId, String conceptTag);
}
