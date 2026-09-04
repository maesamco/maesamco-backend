package com.maesamco.content.dailyquiz.application.command;

import java.util.List;
import java.util.Objects;

public record DailyQuizConceptSelectionCommand(
        // 정식 문제 풀이 이력 존재 여부
        boolean hasProblemProgress,
        // 현재 오답 상태인 문제들의 개념
        List<String> wrongConcepts,
        // 정답 처리한 문제들의 개념
        List<String> solvedConcepts,
        // 풀이 이력이 없는 신규 사용자의 관심 개념 목록
        List<String> interestConcepts
) {

    public DailyQuizConceptSelectionCommand {
        wrongConcepts = List.copyOf(
                Objects.requireNonNull(wrongConcepts, "오답 개념 목록은 필수입니다.")
        );
        solvedConcepts = List.copyOf(
                Objects.requireNonNull(solvedConcepts, "정답 개념 목록은 필수입니다.")
        );
        interestConcepts = List.copyOf(
                Objects.requireNonNull(interestConcepts, "관심 개념 목록은 필수입니다.")
        );

        if (hasProblemProgress && !interestConcepts.isEmpty()) {
            throw new IllegalArgumentException("풀이 이력이 있는 사용자는 관심 개념을 사용할 수 없습니다.");
        }
        if (!hasProblemProgress && (!wrongConcepts.isEmpty() || !solvedConcepts.isEmpty())) {
            throw new IllegalArgumentException("풀이 이력이 없는 사용자는 오답 또는 정답 개념을 사용할 수 없습니다.");
        }
    }

    public static DailyQuizConceptSelectionCommand withLearningHistory(
            List<String> wrongConcepts,
            List<String> solvedConcepts
    ) {
        return new DailyQuizConceptSelectionCommand(
                true,
                wrongConcepts,
                solvedConcepts,
                List.of()
        );
    }

    public static DailyQuizConceptSelectionCommand forNewUser(
            List<String> interestConcepts
    ) {
        return new DailyQuizConceptSelectionCommand(
                false,
                List.of(),
                List.of(),
                interestConcepts
        );
    }
}
