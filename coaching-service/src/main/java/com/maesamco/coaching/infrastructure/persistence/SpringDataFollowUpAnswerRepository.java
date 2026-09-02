package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.FollowUpAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataFollowUpAnswerRepository extends JpaRepository<FollowUpAnswer, UUID> {

    Optional<FollowUpAnswer> findByFollowUpQuestionId(UUID followUpQuestionId);
}
