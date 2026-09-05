package com.maesamco.content.dailyquiz.application.command;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(attemptDate, "퀴즈 날짜는 필수입니다.");

        questionIds = List.copyOf(Objects.requireNonNull(questionIds, "문항 ID 목록은 필수입니다."));

        if (questionIds.size() < MINIMUM_QUESTION_COUNT) {
            throw new IllegalArgumentException("문항 수는 최소 3개 이상이어야 합니다.");
        }

        if (questionIds.size() > TARGET_QUESTION_COUNT) {
            throw new IllegalArgumentException("문항 수는 최대 5개입니다.");
        }

        if (questionIds.stream().distinct().count() != questionIds.size()) {
            throw new IllegalArgumentException("중복된 문항이 존재합니다.");
        }
    }

    public static DailyQuizSetCreateCommand from(
            UUID userId,
            LocalDate attemptDate,
            List<UUID> questionIds
    ) {
        return new DailyQuizSetCreateCommand(userId, attemptDate, questionIds);
    }
}
