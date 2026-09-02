package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.FollowUpQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataFollowUpQuestionRepository extends JpaRepository<FollowUpQuestion, UUID> {

    Optional<FollowUpQuestion> findByExplanationId(UUID explanationId);
}
