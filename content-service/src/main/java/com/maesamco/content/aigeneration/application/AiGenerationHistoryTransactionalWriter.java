package com.maesamco.content.aigeneration.application;

import com.maesamco.content.aigeneration.domain.entity.AiGenerationHistory;
import com.maesamco.content.aigeneration.domain.repository.AiGenerationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiGenerationHistoryTransactionalWriter {

    private final AiGenerationHistoryRepository historyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AiGenerationHistory history) {
        historyRepository.save(history);
    }
}