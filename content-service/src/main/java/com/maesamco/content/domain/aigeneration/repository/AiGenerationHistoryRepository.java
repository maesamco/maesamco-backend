package com.maesamco.content.domain.aigeneration.repository;

import com.maesamco.content.domain.aigeneration.entity.AiGenerationHistory;

public interface AiGenerationHistoryRepository {

    AiGenerationHistory save(AiGenerationHistory history);
}
