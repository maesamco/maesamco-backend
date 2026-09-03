package com.maesamco.coaching.presentation.api_controller;

import com.maesamco.coaching.application.facade.HintGenerationFacade.HintGenerationResult;
import com.maesamco.coaching.domain.entity.Hint;

import java.time.Instant;
import java.util.UUID;

/**
 * 코칭 서비스 API 명세 1·2번 API(오답 힌트 요청·목록 조회) 응답 바디.
 */
public record HintResponse(UUID coachingSessionId, UUID hintId, int stage, String content,
                            boolean skipAvailable, Instant createdAt) {

    public static HintResponse from(HintGenerationResult result) {
        Hint hint = result.hint();
        return new HintResponse(
                result.coachingSessionId(), hint.getId(), hint.getStage(), hint.getContent(),
                result.skipAvailable(), hint.getCreatedAt()
        );
    }
}
