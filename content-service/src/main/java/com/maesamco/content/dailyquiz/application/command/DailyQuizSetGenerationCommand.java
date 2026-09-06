package com.maesamco.content.dailyquiz.application.command;

import com.maesamco.content.dailyquiz.domain.DailyQuizConceptCandidates;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 사용자와 날짜, 개념 후보를 묶어 Daily Quiz 세트 생성 전체 흐름을 시작하는 입력입니다.
 */
public record DailyQuizSetGenerationCommand(
        UUID userId,
        LocalDate attemptDate,
        DailyQuizConceptCandidates conceptCandidates
) {

    public DailyQuizSetGenerationCommand {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(attemptDate, "퀴즈 날짜는 필수입니다.");
        Objects.requireNonNull(conceptCandidates, "개념 선정 후보는 필수입니다.");
    }

    public static DailyQuizSetGenerationCommand from(
            UUID userId,
            LocalDate attemptDate,
            DailyQuizConceptCandidates conceptCandidates
    ) {
        return new DailyQuizSetGenerationCommand(
                userId,
                attemptDate,
                conceptCandidates
        );
    }
}
