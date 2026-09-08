package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.domain.entity.AiFeedback;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 종합 피드백 조회/재시도(코칭 서비스 API 명세 6번 API) 공통 응답.
 * coachingSessionId는 내부 식별자라 응답에 포함하지 않는다(API 명세 그대로).
 */
public record AiFeedbackResponse(
        UUID feedbackId,
        JsonNode understoodConcepts,
        JsonNode explanationGaps,
        JsonNode weakConcepts,
        JsonNode syntaxToImprove,
        JsonNode recommendedProblems,
        String nextDirection,
        Instant createdAt
) {

    public static AiFeedbackResponse from(AiFeedback feedback) {
        return new AiFeedbackResponse(
                feedback.getId(),
                feedback.getUnderstoodConcepts(),
                feedback.getExplanationGaps(),
                feedback.getWeakConcepts(),
                feedback.getSyntaxToImprove(),
                feedback.getRecommendedProblems(),
                feedback.getNextDirection(),
                feedback.getCreatedAt()
        );
    }
}
