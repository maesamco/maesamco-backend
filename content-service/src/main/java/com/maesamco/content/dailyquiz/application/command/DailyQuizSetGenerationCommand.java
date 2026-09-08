package com.maesamco.content.dailyquiz.application.command;

import com.maesamco.content.dailyquiz.domain.DailyQuizConceptCandidates;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

import java.time.LocalDate;
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
        if (userId == null) {
            throw invalidInput("사용자 ID는 필수입니다.");
        }
        if (attemptDate == null) {
            throw invalidInput("퀴즈 날짜는 필수입니다.");
        }
        if (conceptCandidates == null) {
            throw invalidInput("개념 선정 후보는 필수입니다.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
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
