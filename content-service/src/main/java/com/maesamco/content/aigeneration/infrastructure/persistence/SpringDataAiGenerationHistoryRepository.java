package com.maesamco.content.aigeneration.infrastructure.persistence;

import com.maesamco.content.aigeneration.domain.entity.AiGenerationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataAiGenerationHistoryRepository extends JpaRepository<AiGenerationHistory, UUID> {
}
