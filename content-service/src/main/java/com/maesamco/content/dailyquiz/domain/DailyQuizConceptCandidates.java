package com.maesamco.content.dailyquiz.domain;

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
        List<String> solvedConcepts,
        // 풀이 이력이 없는 신규 사용자의 관심 개념 목록
        List<String> interestConcepts
) {

    public DailyQuizConceptCandidates {
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

    public static DailyQuizConceptCandidates fromProblemProgress(
            List<String> wrongConcepts,
            List<String> solvedConcepts
    ) {
        return new DailyQuizConceptCandidates(
                true,
                wrongConcepts,
                solvedConcepts,
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
