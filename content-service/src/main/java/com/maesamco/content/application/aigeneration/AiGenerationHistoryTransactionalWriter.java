package com.maesamco.content.application.aigeneration;

import com.maesamco.content.domain.aigeneration.entity.AiGenerationHistory;
import com.maesamco.content.domain.aigeneration.repository.AiGenerationHistoryRepository;
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