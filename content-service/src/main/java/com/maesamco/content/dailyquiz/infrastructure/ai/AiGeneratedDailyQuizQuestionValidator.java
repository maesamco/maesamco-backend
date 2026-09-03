package com.maesamco.content.dailyquiz.infrastructure.ai;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizProblemType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AiGeneratedDailyQuizQuestionValidator {

    private static final int MULTIPLE_CHOICE_OPTION_COUNT = 4;
    private static final String FILL_IN_BLANK_MARKER = "___";
    private static final int FILL_IN_BLANK_MARKER_COUNT = 1;

    static void validate(AiGeneratedDailyQuizQuestionResponse response) {
        if (response.problemType() == null) {
            throw new IllegalArgumentException("문제 타입은 필수입니다.");
        }

        if (response.questionText() == null || response.questionText().isBlank()) {
            throw new IllegalArgumentException("문제 내용은 필수입니다.");
        }

        if (response.answer() == null || response.answer().isBlank()) {
            throw new IllegalArgumentException("정답은 필수입니다.");
        }

        switch (response.problemType()) {
            case MULTIPLE_CHOICE -> validateMultipleChoice(response);
            case FILL_IN_BLANK -> validateFillInBlank(response);
            case SHORT_ANSWER -> validateShortAnswer(response);
        }
    }

    private static void validateMultipleChoice(AiGeneratedDailyQuizQuestionResponse response) {
        if (response.choices() == null) {
            throw new IllegalArgumentException("객관식 선택지는 필수입니다.");
        }

        if (response.choices().size() != MULTIPLE_CHOICE_OPTION_COUNT) {
            throw new IllegalArgumentException(
                    "객관식 선택지는 정확히 " + MULTIPLE_CHOICE_OPTION_COUNT + "개여야 합니다."
            );
        }

        boolean hasBlankChoice = response.choices().stream()
                .anyMatch(choice -> choice == null || choice.isBlank());
        if (hasBlankChoice) {
            throw new IllegalArgumentException("객관식 선택지는 비어 있을 수 없습니다.");
        }

        int uniqueChoiceCount = new HashSet<>(response.choices()).size();
        if (uniqueChoiceCount != response.choices().size()) {
            throw new IllegalArgumentException("중복 선택지가 있습니다.");
        }

        if (!response.choices().contains(response.answer())) {
            throw new IllegalArgumentException("객관식 정답은 선택지 중 하나여야 합니다.");
        }

        if (hasValues(response.allowedAnswerVariants())) {
            throw new IllegalArgumentException("허용 답안 표현은 단답형 문제에서만 사용할 수 있습니다.");
        }
    }

    private static void validateFillInBlank(AiGeneratedDailyQuizQuestionResponse response) {
        if (hasValues(response.choices())) {
            throw new IllegalArgumentException("선택지는 객관식 문제에서만 사용할 수 있습니다.");
        }

        int markerCount = countOccurrences(response.questionText(), FILL_IN_BLANK_MARKER);
        if (markerCount != FILL_IN_BLANK_MARKER_COUNT) {
            throw new IllegalArgumentException(
                    "빈칸형 문제에는 " + FILL_IN_BLANK_MARKER + "가 정확히 한 번 있어야 합니다."
            );
        }

        if (hasValues(response.allowedAnswerVariants())) {
            throw new IllegalArgumentException("허용 답안 표현은 단답형 문제에서만 사용할 수 있습니다.");
        }
    }

    private static void validateShortAnswer(AiGeneratedDailyQuizQuestionResponse response) {
        if (hasValues(response.choices())) {
            throw new IllegalArgumentException("선택지는 객관식 문제에서만 사용할 수 있습니다.");
        }

        if (!hasValues(response.allowedAnswerVariants())) {
            return;
        }

        boolean hasBlankVariant = response.allowedAnswerVariants().stream()
                .anyMatch(variant -> variant == null || variant.isBlank());
        if (hasBlankVariant) {
            throw new IllegalArgumentException("허용 답안 표현은 비어 있을 수 없습니다.");
        }

        int uniqueVariantCount = new HashSet<>(response.allowedAnswerVariants()).size();
        if (uniqueVariantCount != response.allowedAnswerVariants().size()) {
            throw new IllegalArgumentException("허용 답안 표현은 중복될 수 없습니다.");
        }

        if (response.allowedAnswerVariants().contains(response.answer())) {
            throw new IllegalArgumentException("대표 정답을 허용 답안 표현에 중복해서 넣을 수 없습니다.");
        }
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int fromIndex = 0;

        while ((fromIndex = text.indexOf(target, fromIndex)) >= 0) {
            count++;
            fromIndex += target.length();
        }

        return count;
    }

    private static boolean hasValues(List<?> values) {
        return values != null && !values.isEmpty();
    }
}
