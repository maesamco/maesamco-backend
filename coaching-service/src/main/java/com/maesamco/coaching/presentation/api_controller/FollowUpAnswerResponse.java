package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.domain.entity.FollowUpAnswer;

import java.time.Instant;
import java.util.UUID;

/**
 * 코칭 서비스 API 명세 4번 API(설명·역질문 조회) 응답에 중첩되는 답변 정보 — 아직
 * 답변하지 않았으면 null.
 */
public record FollowUpAnswerResponse(UUID followUpAnswerId, String answerText, Instant answeredAt) {

    public static FollowUpAnswerResponse from(FollowUpAnswer followUpAnswer) {
        return new FollowUpAnswerResponse(
                followUpAnswer.getId(), followUpAnswer.getAnswerText(), followUpAnswer.getAnsweredAt()
        );
    }
}
