package com.maesamco.content.dailyquiz.application.query;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 사용자별 Daily Quiz 출제 개념 후보를 조회하기 위한 조건
 */
public record DailyQuizConceptCandidatesGetQuery(
        UUID userId,
        LocalDate attemptDate
) {

    public DailyQuizConceptCandidatesGetQuery {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(attemptDate, "퀴즈 날짜는 필수입니다.");
    }

    public static DailyQuizConceptCandidatesGetQuery from(UUID userId, LocalDate attemptDate) {
        return new DailyQuizConceptCandidatesGetQuery(userId, attemptDate);
    }
}
