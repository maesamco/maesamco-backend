package com.maesamco.content.dailyquiz.application.command;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.MINIMUM_QUESTION_COUNT;
import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.TARGET_QUESTION_COUNT;

// 세트 생성 요청 정보
public record DailyQuizSetCreateCommand(
        UUID userId,
        // 퀴즈 날짜
        LocalDate attemptDate,
        // 각 퀴즈 문항 ID
        List<UUID> questionIds
) {
    public DailyQuizSetCreateCommand {
        if (userId == null) {
            throw invalidInput("사용자 ID는 필수입니다.");
        }
        if (attemptDate == null) {
            throw invalidInput("퀴즈 날짜는 필수입니다.");
        }
        if (questionIds == null) {
            throw invalidInput("문항 ID 목록은 필수입니다.");
        }
        if (questionIds.contains(null)) {
            throw invalidInput("문항 ID는 비어 있을 수 없습니다.");
        }

        questionIds = List.copyOf(questionIds);

        if (questionIds.size() < MINIMUM_QUESTION_COUNT) {
            throw invalidInput("문항 수는 최소 3개 이상이어야 합니다.");
        }

        if (questionIds.size() > TARGET_QUESTION_COUNT) {
            throw invalidInput("문항 수는 최대 5개입니다.");
        }

        if (questionIds.stream().distinct().count() != questionIds.size()) {
            throw invalidInput("중복된 문항이 존재합니다.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }

    public static DailyQuizSetCreateCommand from(
            UUID userId,
            LocalDate attemptDate,
            List<UUID> questionIds
    ) {
        return new DailyQuizSetCreateCommand(userId, attemptDate, questionIds);
    }
}
