package com.maesamco.content.domain.dailyquiz;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;

import java.util.List;
import java.util.Objects;

/**
 * Daily Quiz 개념 슬롯 선정에 사용할 후보 데이터입니다.
 */
public record DailyQuizConceptCandidates(
        // 정식 문제 풀이 이력 존재 여부
        boolean hasProblemProgress,
        // 현재 오답 상태인 문제들의 개념
        List<String> wrongConcepts,
        // 정답 처리한 문제들의 개념
        List<String> correctConcepts,
        // 풀이 이력이 없는 신규 사용자의 관심 개념 목록
        List<String> interestConcepts
) {

    public DailyQuizConceptCandidates {
        if (wrongConcepts == null) {
            throw invalidInput("오답 개념 목록은 필수입니다.");
        }
        if (correctConcepts == null) {
            throw invalidInput("정답 개념 목록은 필수입니다.");
        }
        if (interestConcepts == null) {
            throw invalidInput("관심 개념 목록은 필수입니다.");
        }
        if (wrongConcepts.stream().anyMatch(Objects::isNull)) {
            throw invalidInput("오답 개념은 비어 있을 수 없습니다.");
        }
        if (correctConcepts.stream().anyMatch(Objects::isNull)) {
            throw invalidInput("정답 개념은 비어 있을 수 없습니다.");
        }
        if (interestConcepts.stream().anyMatch(Objects::isNull)) {
            throw invalidInput("관심 개념은 비어 있을 수 없습니다.");
        }

        wrongConcepts = List.copyOf(wrongConcepts);
        correctConcepts = List.copyOf(correctConcepts);
        interestConcepts = List.copyOf(interestConcepts);

        if (hasProblemProgress && !interestConcepts.isEmpty()) {
            throw invalidInput("풀이 이력이 있는 사용자는 관심 개념을 사용할 수 없습니다.");
        }
        if (!hasProblemProgress && (!wrongConcepts.isEmpty() || !correctConcepts.isEmpty())) {
            throw invalidInput("풀이 이력이 없는 사용자는 오답 또는 정답 개념을 사용할 수 없습니다.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }

    public static DailyQuizConceptCandidates fromProblemProgress(
            List<String> wrongConcepts,
            List<String> correctConcepts
    ) {
        return new DailyQuizConceptCandidates(
                true,
                wrongConcepts,
                correctConcepts,
                List.of()
        );
    }

    public static DailyQuizConceptCandidates fromInterests(
            List<String> interestConcepts
    ) {
        return new DailyQuizConceptCandidates(
                false,
                List.of(),
                List.of(),
                interestConcepts
        );
    }
}
