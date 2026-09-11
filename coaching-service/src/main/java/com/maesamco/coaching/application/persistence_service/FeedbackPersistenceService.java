package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.entity.AiCallHistory;
import com.maesamco.coaching.domain.entity.AiCallPurpose;
import com.maesamco.coaching.domain.entity.AiFeedback;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.AiFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
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
 *
 * 이슈 #62 — WeakConcept 갱신 로직 자체는 HintGenerationFacade(Content Service 개념 태그
 * 출처)와 공유하는 WeakConceptPersistenceService로 옮겼다. 같은 트랜잭션 안에서 호출되므로
 * 원자성은 그대로 유지된다.
 */
@Service
@Transactional
public class FeedbackPersistenceService {

    private final AiCallHistoryRepository aiCallHistoryRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final WeakConceptPersistenceService weakConceptPersistenceService;

    public FeedbackPersistenceService(
            AiCallHistoryRepository aiCallHistoryRepository,
            AiFeedbackRepository aiFeedbackRepository,
            WeakConceptPersistenceService weakConceptPersistenceService
    ) {
        this.aiCallHistoryRepository = aiCallHistoryRepository;
        this.aiFeedbackRepository = aiFeedbackRepository;
        this.weakConceptPersistenceService = weakConceptPersistenceService;
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

        weakConceptPersistenceService.recordOccurrences(userId, toStringList(weakConcepts));
    }

    private List<String> toStringList(JsonNode arrayNode) {
        List<String> tags = new ArrayList<>();
        for (JsonNode tagNode : arrayNode) {
            if (tagNode != null && !tagNode.isNull() && tagNode.isString()) {
                tags.add(tagNode.asString());
            }
        }
        return tags;
    }
}
