package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.FollowUpAnswerFacade;

import java.time.Instant;
import java.util.UUID;

/**
 * 코칭 서비스 API 명세 5번 API(역질문 답변 등록) 응답 바디. coachingSessionStatus는 이
 * 응답 시점에 항상 COMPLETED다 — 답변 등록이 곧 세션 완료 트리거이기 때문(이슈 #51).
 */
public record FollowUpAnswerRegisterResponse(
        UUID followUpAnswerId, String answerText, Instant answeredAt, String coachingSessionStatus
) {

    public static FollowUpAnswerRegisterResponse from(FollowUpAnswerFacade.FollowUpAnswerRegisterResult result) {
        return new FollowUpAnswerRegisterResponse(
                result.followUpAnswer().getId(),
                result.followUpAnswer().getAnswerText(),
                result.followUpAnswer().getAnsweredAt(),
                result.coachingSessionStatus().name()
        );
    }
}
