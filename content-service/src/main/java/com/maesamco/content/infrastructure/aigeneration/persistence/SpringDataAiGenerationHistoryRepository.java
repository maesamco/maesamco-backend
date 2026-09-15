package com.maesamco.content.infrastructure.aigeneration.persistence;

import com.maesamco.content.domain.aigeneration.entity.AiGenerationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataAiGenerationHistoryRepository extends JpaRepository<AiGenerationHistory, UUID> {
}
