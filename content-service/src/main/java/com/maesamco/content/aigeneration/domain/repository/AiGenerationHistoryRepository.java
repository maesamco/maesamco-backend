package com.maesamco.content.aigeneration.domain.repository;

import com.maesamco.content.aigeneration.domain.entity.AiGenerationHistory;

public interface AiGenerationHistoryRepository {

    AiGenerationHistory save(AiGenerationHistory history);
}
