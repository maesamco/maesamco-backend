package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.ExplanationGenerationFacade.ExplanationRegistrationResult;

import java.time.Instant;
import java.util.UUID;

/**
 * 60초 설명 등록(코칭 서비스 API 명세 3번 API) 응답. AI 역질문 생성이 실패해도 설명 자체는
 * 이미 저장된 상태이므로 followUpQuestion만 null로 반환한다(API 명세).
 */
public record ExplanationRegisterResponse(
        UUID explanationId, String content, Instant createdAt, FollowUpQuestionResponse followUpQuestion
) {

    public static ExplanationRegisterResponse from(ExplanationRegistrationResult result) {
        return new ExplanationRegisterResponse(
                result.explanation().getId(),
                result.explanation().getContent(),
                result.explanation().getCreatedAt(),
                result.followUpQuestion() == null ? null : FollowUpQuestionResponse.from(result.followUpQuestion())
        );
    }
}
