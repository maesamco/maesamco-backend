package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.WeakTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataWeakTagRepository extends JpaRepository<WeakTag, UUID> {

    Optional<WeakTag> findByUserIdAndTag(UUID userId, String tag);
}
