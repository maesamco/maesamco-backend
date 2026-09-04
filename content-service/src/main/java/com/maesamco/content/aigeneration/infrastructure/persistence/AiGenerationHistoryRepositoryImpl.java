package com.maesamco.content.aigeneration.infrastructure.persistence;

import com.maesamco.content.aigeneration.domain.entity.AiGenerationHistory;
import com.maesamco.content.aigeneration.domain.repository.AiGenerationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AiGenerationHistoryRepositoryImpl implements AiGenerationHistoryRepository {

    private final SpringDataAiGenerationHistoryRepository springDataRepository;

    @Override
    public AiGenerationHistory save(AiGenerationHistory history) {
        return springDataRepository.save(history);
    }
}
