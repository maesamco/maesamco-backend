package com.maesamco.content.dailyquiz.application.query;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

import java.util.UUID;

/**
 * 인증된 사용자의 오늘 Daily Quiz를 조회하기 위한 Query DTO
 */
public record DailyQuizGetQuery(
        UUID userId
) {

    public DailyQuizGetQuery {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID는 필수입니다.");
        }
    }

    public static DailyQuizGetQuery from(UUID userId) {
        return new DailyQuizGetQuery(userId);
    }
}
