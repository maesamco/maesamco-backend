package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.FollowUpAnswer;

import java.util.Optional;
import java.util.UUID;

public interface FollowUpAnswerRepository {

    FollowUpAnswer save(FollowUpAnswer followUpAnswer);

    Optional<FollowUpAnswer> findByFollowUpQuestionId(UUID followUpQuestionId);
}
