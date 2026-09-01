package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.FollowUpQuestion;

import java.util.Optional;
import java.util.UUID;

public interface FollowUpQuestionRepository {

    FollowUpQuestion save(FollowUpQuestion followUpQuestion);

    Optional<FollowUpQuestion> findByExplanationId(UUID explanationId);
}
