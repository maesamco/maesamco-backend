package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.domain.entity.Hint;

import java.time.Instant;
import java.util.UUID;

/**
 * 코칭 서비스 API 명세 2번 API(힌트 목록 조회) 응답 배열의 항목 — coachingSessionId/
 * skipAvailable 없이 힌트 자체 정보만 담는다(HintResponse와 다른 이유).
 */
public record HintListItemResponse(UUID hintId, int stage, String content, Instant createdAt) {

    public static HintListItemResponse from(Hint hint) {
        return new HintListItemResponse(hint.getId(), hint.getStage(), hint.getContent(), hint.getCreatedAt());
    }
}
