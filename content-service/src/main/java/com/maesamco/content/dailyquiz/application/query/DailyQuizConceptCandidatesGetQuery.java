package com.maesamco.content.dailyquiz.application.query;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자별 Daily Quiz 출제 개념 후보를 조회하기 위한 조건
 */
public record DailyQuizConceptCandidatesGetQuery(
        UUID userId,
        LocalDate attemptDate
) {

    public DailyQuizConceptCandidatesGetQuery {
        if (userId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "사용자 ID는 필수입니다."
            );
        }
        if (attemptDate == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "퀴즈 날짜는 필수입니다."
            );
        }
    }

    public static DailyQuizConceptCandidatesGetQuery from(UUID userId, LocalDate attemptDate) {
        return new DailyQuizConceptCandidatesGetQuery(userId, attemptDate);
    }
}
