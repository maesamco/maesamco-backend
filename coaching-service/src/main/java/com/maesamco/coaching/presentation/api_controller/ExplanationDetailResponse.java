package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.query_service.ExplanationQueryService.ExplanationQueryResult;

import java.time.Instant;
import java.util.UUID;

/**
 * 설명·역질문 조회(코칭 서비스 API 명세 4번 API) 응답. 아직 답변하지 않았다면
 * followUpAnswer는 null이다(나중에 이어서 답변 가능).
 */
public record ExplanationDetailResponse(
        UUID explanationId,
        String content,
        Instant createdAt,
        FollowUpQuestionResponse followUpQuestion,
        FollowUpAnswerResponse followUpAnswer
) {

    public static ExplanationDetailResponse from(ExplanationQueryResult result) {
        return new ExplanationDetailResponse(
                result.explanation().getId(),
                result.explanation().getContent(),
                result.explanation().getCreatedAt(),
                result.followUpQuestion() == null ? null : FollowUpQuestionResponse.from(result.followUpQuestion()),
                result.followUpAnswer() == null ? null : FollowUpAnswerResponse.from(result.followUpAnswer())
        );
    }
}
