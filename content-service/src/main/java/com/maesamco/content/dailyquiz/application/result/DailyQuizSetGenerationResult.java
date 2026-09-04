package com.maesamco.content.dailyquiz.application.result;

import java.util.UUID;

import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.MINIMUM_QUESTION_COUNT;
import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.TARGET_QUESTION_COUNT;

public record DailyQuizSetGenerationResult(
        DailyQuizSetGenerationStatus status,
        UUID attemptId,
        int questionCount
) {

    public DailyQuizSetGenerationResult {
        if (status == null) {
            throw new IllegalArgumentException("Daily Quiz 세트 생성 상태는 필수입니다.");
        }
        if (questionCount < 0 || questionCount > TARGET_QUESTION_COUNT) {
            throw new IllegalArgumentException(
                    "확보한 문항 수는 0개 이상 " + TARGET_QUESTION_COUNT + "개 이하여야 합니다."
            );
        }

        switch (status) {
            case CREATED -> validateCreated(attemptId, questionCount);
            case ALREADY_EXISTS, NO_AVAILABLE_CONCEPTS ->
                    validateNotCreated(attemptId, questionCount, status);
            case INSUFFICIENT_QUESTIONS ->
                    validateInsufficientQuestions(attemptId, questionCount);
        }
    }

    public static DailyQuizSetGenerationResult created(
            UUID attemptId,
            int questionCount
    ) {
        return new DailyQuizSetGenerationResult(
                DailyQuizSetGenerationStatus.CREATED,
                attemptId,
                questionCount
        );
    }

    public static DailyQuizSetGenerationResult alreadyExists() {
        return new DailyQuizSetGenerationResult(
                DailyQuizSetGenerationStatus.ALREADY_EXISTS,
                null,
                0
        );
    }

    public static DailyQuizSetGenerationResult noAvailableConcepts() {
        return new DailyQuizSetGenerationResult(
                DailyQuizSetGenerationStatus.NO_AVAILABLE_CONCEPTS,
                null,
                0
        );
    }

    public static DailyQuizSetGenerationResult insufficientQuestions(int questionCount) {
        return new DailyQuizSetGenerationResult(
                DailyQuizSetGenerationStatus.INSUFFICIENT_QUESTIONS,
                null,
                questionCount
        );
    }

    private static void validateCreated(UUID attemptId, int questionCount) {
        if (attemptId == null) {
            throw new IllegalArgumentException("생성된 Daily Quiz 세트 ID는 필수입니다.");
        }
        if (questionCount < MINIMUM_QUESTION_COUNT) {
            throw new IllegalArgumentException(
                    "생성된 Daily Quiz 세트는 최소 " + MINIMUM_QUESTION_COUNT + "개의 문항이 필요합니다."
            );
        }
    }

    private static void validateNotCreated(
            UUID attemptId,
            int questionCount,
            DailyQuizSetGenerationStatus status
    ) {
        if (attemptId != null || questionCount != 0) {
            throw new IllegalArgumentException(
                    status + " 상태에는 생성된 세트 ID나 확보한 문항 수가 있을 수 없습니다."
            );
        }
    }

    private static void validateInsufficientQuestions(UUID attemptId, int questionCount) {
        if (attemptId != null) {
            throw new IllegalArgumentException("최소 문항 미달 상태에는 생성된 세트 ID가 있을 수 없습니다.");
        }
        if (questionCount >= MINIMUM_QUESTION_COUNT) {
            throw new IllegalArgumentException(
                    "최소 문항 미달 상태의 문항 수는 " + MINIMUM_QUESTION_COUNT + "개 미만이어야 합니다."
            );
        }
    }
}
