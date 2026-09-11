package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.domain.repository.WeakConceptRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/**
 * 취약 개념 집계(WeakConcept) 갱신 — AI 종합 피드백(FeedbackPersistenceService, AI가 판단한
 * weakConcepts 태그)과 힌트 생성(HintGenerationFacade, 이슈 #62 — attemptNo>=8로
 * skipAvailable일 때 Content Service의 문제 개념 태그)이 서로 다른 출처의 태그로 같은
 * find-or-create-with-race-safety 갱신 로직을 필요로 해서 공통으로 뺐다.
 */
@Service
@Transactional
public class WeakConceptPersistenceService {

    private final WeakConceptRepository weakConceptRepository;

    public WeakConceptPersistenceService(WeakConceptRepository weakConceptRepository) {
        this.weakConceptRepository = weakConceptRepository;
    }

    /**
     * 각 태그에 대해 기존 집계 행이 있으면 recordOccurrence()로 갱신하고, 없으면 새로
     * 만든다. 조회 후 생성 사이의 동시성 경합으로 save()가 WEAK_CONCEPT_ALREADY_EXISTS를
     * 던지면(WeakConceptRepositoryImpl의 UNIQUE 위반 안전망), 그 사이 다른 트랜잭션이
     * 먼저 만든 행을 다시 조회해 recordOccurrence()로 갱신한다.
     */
    public void recordOccurrences(UUID userId, Collection<String> conceptTags) {
        for (String conceptTag : conceptTags) {
            if (conceptTag == null || conceptTag.isBlank()) {
                continue;
            }
            recordOccurrence(userId, conceptTag.trim());
        }
    }

    private void recordOccurrence(UUID userId, String conceptTag) {
        var existing = weakConceptRepository.findByUserIdAndConceptTag(userId, conceptTag);
        if (existing.isPresent()) {
            existing.get().recordOccurrence();
            weakConceptRepository.save(existing.get());
            return;
        }

        try {
            weakConceptRepository.save(WeakConcept.create(userId, conceptTag));
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.WEAK_CONCEPT_ALREADY_EXISTS) {
                throw e;
            }
            weakConceptRepository.findByUserIdAndConceptTag(userId, conceptTag)
                    .ifPresent(concept -> {
                        concept.recordOccurrence();
                        weakConceptRepository.save(concept);
                    });
        }
    }
}
