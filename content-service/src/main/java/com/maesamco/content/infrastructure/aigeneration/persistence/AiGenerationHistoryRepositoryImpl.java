package com.maesamco.content.infrastructure.aigeneration.persistence;

import com.maesamco.content.domain.aigeneration.entity.AiGenerationHistory;
import com.maesamco.content.domain.aigeneration.repository.AiGenerationHistoryRepository;
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
