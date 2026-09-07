package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.domain.entity.WeakConcept;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import com.maesamco.coaching.domain.repository.WeakConceptRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * AI 종합 피드백 저장(PR #98 자가 리뷰 반영, 용현님 P1) — AiCallHistory(SUCCESS) +
 * AiFeedback + WeakConcept 집계를 한 트랜잭션으로 묶는 순수 DB 로직. LLM/Judge 호출이
 * 전혀 없어 Facade가 아니라 PersistenceService다(FollowUpAnswerPersistenceService와
 * 동일한 이유).
 *
 * 이전엔 FeedbackGenerationFacade가 AiCallHistory(SUCCESS)를 AiFeedback/WeakConcept
 * 저장보다 먼저 기록해서, 그 뒤 저장이 실패해도 이력은 이미 SUCCESS로 남는 문제가 있었다.
 * 이제 셋을 한 트랜잭션으로 묶어서, 이 메서드가 실패하면 AiCallHistory까지 함께 롤백되고
 * 호출자(FeedbackGenerationFacade)가 그 예외를 잡아 별도로 FAILED 이력을 남긴다 — "SUCCESS로
 * 기록됐는데 실제로는 일부만 저장됨" 상태가 나올 수 없다.
 */
@Service
@Transactional
public class FeedbackPersistenceService {

    private final AiCallHistoryRepository aiCallHistoryRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final WeakConceptRepository weakConceptRepository;

    public FeedbackPersistenceService(
            AiCallHistoryRepository aiCallHistoryRepository,
            AiFeedbackRepository aiFeedbackRepository,
            WeakConceptRepository weakConceptRepository
    ) {
        this.aiCallHistoryRepository = aiCallHistoryRepository;
        this.aiFeedbackRepository = aiFeedbackRepository;
        this.weakConceptRepository = weakConceptRepository;
    }

    /**
     * FeedbackGenerationFacade가 LLM 응답을 성공적으로 파싱한 뒤에만 호출한다 — 이 메서드
     * 자체는 이미 파싱된 값을 저장하는 것 외엔 아무 판단도 하지 않는다.
     */
    public void saveFeedback(
            UUID coachingSessionId, UUID userId, String modelName, String promptVersion, Integer tokenUsage,
            JsonNode understoodConcepts, JsonNode explanationGaps, JsonNode weakConcepts,
            JsonNode syntaxToImprove, JsonNode recommendedProblems, String nextDirection
    ) {
        aiCallHistoryRepository.save(AiCallHistory.create(
                coachingSessionId, AiCallPurpose.FEEDBACK, modelName, promptVersion,
                "SUCCESS", null, tokenUsage, null, 0
        ));

        aiFeedbackRepository.save(AiFeedback.create(
                coachingSessionId, understoodConcepts, explanationGaps, weakConcepts,
                syntaxToImprove, recommendedProblems, nextDirection
        ));

        recordWeakConcepts(userId, weakConcepts);
    }

    /**
     * weakConcepts 배열의 각 태그에 대해 기존 집계 행이 있으면 recordOccurrence()로
     * 갱신하고, 없으면 새로 만든다. 조회 후 생성 사이의 동시성 경합으로 WeakConceptRepository
     * .save()가 WEAK_CONCEPT_ALREADY_EXISTS를 던지면(WeakConceptRepositoryImpl의 UNIQUE
     * 위반 안전망), 그 사이 다른 트랜잭션이 먼저 만든 행을 다시 조회해 recordOccurrence()로
     * 갱신한다.
     */
    private void recordWeakConcepts(UUID userId, JsonNode weakConcepts) {
        for (JsonNode tagNode : weakConcepts) {
            if (tagNode == null || tagNode.isNull() || !tagNode.isString()) {
                continue;
            }
            String conceptTag = tagNode.asString().trim();
            if (conceptTag.isBlank()) {
                continue;
            }
            recordWeakConcept(userId, conceptTag);
        }
    }

    private void recordWeakConcept(UUID userId, String conceptTag) {
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
