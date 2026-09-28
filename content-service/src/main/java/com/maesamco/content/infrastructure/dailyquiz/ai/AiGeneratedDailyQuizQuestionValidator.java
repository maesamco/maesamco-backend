package com.maesamco.content.infrastructure.dailyquiz.ai;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AiGeneratedDailyQuizQuestionValidator {

    private static final int MULTIPLE_CHOICE_OPTION_COUNT = 4;
    private static final String FILL_IN_BLANK_MARKER = "___";
    private static final int FILL_IN_BLANK_MARKER_COUNT = 1;
    private static final String CODE_FENCE = "```";

    // 모델이 실제 줄바꿈 대신 백슬래시-n 두 글자를 그대로 응답에 남기는 경우가 있다.
    // 이 리터럴이 남아 있으면 프론트에서 줄바꿈 없이 글자 그대로 노출된다.
    private static final Pattern LITERAL_NEWLINE_ESCAPE = Pattern.compile("\\\\n");

    // 코드 펜스 없이도 코드로 보이는 문항을 걸러내기 위한 대표적인 Java 코드 패턴이다.
    private static final Pattern JAVA_CODE_LIKE_CONTENT = Pattern.compile(
            "\\b(public\\s+(class|static)|System\\.out\\.print(ln)?|void\\s+main\\s*\\()"
    );

    static void validate(
            AiGeneratedDailyQuizQuestionResponse response,
            DailyQuizProblemType requiredProblemType
    ) {
        if (response.problemType() == null) {
            throw new IllegalArgumentException("문제 타입은 필수입니다.");
        }
        if (response.problemType() != requiredProblemType) {
            throw new IllegalArgumentException(
                    "AI 응답 문제 유형이 요청 유형과 일치하지 않습니다. 요청: "
                            + requiredProblemType
                            + ", 응답: "
                            + response.problemType()
            );
        }

        if (response.questionText() == null || response.questionText().isBlank()) {
            throw new IllegalArgumentException("문제 내용은 필수입니다.");
        }

        validateQuestionTextFormatting(response.questionText());

        if (response.answer() == null || response.answer().isBlank()) {
            throw new IllegalArgumentException("정답은 필수입니다.");
        }

        switch (response.problemType()) {
            case MULTIPLE_CHOICE -> validateMultipleChoice(response);
            case FILL_IN_BLANK -> validateFillInBlank(response);
            case SHORT_ANSWER -> validateShortAnswer(response);
        }
    }

    /**
     * 프론트 렌더링과 어긋나는 questionText 형식을 걸러낸다.
     *
     * SYSTEM_PROMPT가 코드는 반드시 ```java 펜스로 감싸라고 지시하지만, 모델이 이를
     * 지키지 않을 때가 있다 — 펜스 없이 코드를 그대로 섞거나, 실제 줄바꿈 대신
     * 리터럴 "\n" 두 글자를 응답에 남기는 경우다. 프론트(QuizPrompt)는 펜스가 있어야만
     * 마크다운/코드블록으로 렌더링하므로, 이런 응답은 화면에서 그대로 깨져 보인다.
     */
    private static void validateQuestionTextFormatting(String questionText) {
        if (LITERAL_NEWLINE_ESCAPE.matcher(questionText).find()) {
            throw new IllegalArgumentException(
                    "문제 지문에 실제 줄바꿈 대신 리터럴 \\n 문자가 포함되어 있습니다."
            );
        }

        int fenceCount = countOccurrences(questionText, CODE_FENCE);
        if (fenceCount % 2 != 0) {
            throw new IllegalArgumentException("코드 펜스(```)가 짝이 맞지 않습니다.");
        }

        if (fenceCount == 0 && JAVA_CODE_LIKE_CONTENT.matcher(questionText).find()) {
            throw new IllegalArgumentException(
                    "문제 지문에 코드가 포함된 것으로 보이지만 ```java 코드 펜스로 감싸지 않았습니다."
            );
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

        List<String> normalizedChoices = normalize(response.choices());
        String normalizedAnswer = response.answer().strip();

        int uniqueChoiceCount = new HashSet<>(normalizedChoices).size();
        if (uniqueChoiceCount != normalizedChoices.size()) {
            throw new IllegalArgumentException("중복 선택지가 있습니다.");
        }

        if (!normalizedChoices.contains(normalizedAnswer)) {
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

        List<String> normalizedVariants = normalize(response.allowedAnswerVariants());
        String normalizedAnswer = response.answer().strip();

        int uniqueVariantCount = new HashSet<>(normalizedVariants).size();
        if (uniqueVariantCount != normalizedVariants.size()) {
            throw new IllegalArgumentException("허용 답안 표현은 중복될 수 없습니다.");
        }

        if (normalizedVariants.contains(normalizedAnswer)) {
            throw new IllegalArgumentException("대표 정답을 허용 답안 표현에 중복해서 넣을 수 없습니다.");
        }
    }

    private static List<String> normalize(List<String> values) {
        return values.stream()
                .map(String::strip)
                .toList();
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
