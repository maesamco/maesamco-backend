package com.maesamco.content.dailyquiz.infrastructure.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * User Service의 Daily Quiz 대상 사용자 cursor 조회 응답 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserQuizTargetPageResponse(
        List<UUID> userIds,
        UUID nextCursor,
        Boolean hasNext
) {
}
