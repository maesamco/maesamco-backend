package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.WeakTag;

import java.util.Optional;
import java.util.UUID;

public interface WeakTagRepository {

    WeakTag save(WeakTag weakTag);

    Optional<WeakTag> findByUserIdAndTag(UUID userId, String tag);
}
