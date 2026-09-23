package com.maesamco.content.domain.dailyquiz;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;

import java.util.List;

import static com.maesamco.content.domain.dailyquiz.DailyQuizPolicy.TARGET_QUESTION_COUNT;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.FILL_IN_BLANK;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.MULTIPLE_CHOICE;
import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.SHORT_ANSWER;

/**
 * Daily Quiz 목표 문항의 문제 유형 분배 정책
 */
public final class DailyQuizQuestionTypePolicy {

    private static final List<DailyQuizProblemType> TARGET_TYPES =
            validateTargetTypes(List.of(
                    MULTIPLE_CHOICE,
                    SHORT_ANSWER,
                    MULTIPLE_CHOICE,
                    SHORT_ANSWER,
                    FILL_IN_BLANK
            ));

    private DailyQuizQuestionTypePolicy() {
    }

    public static List<DailyQuizProblemType> targetTypes() {
        return TARGET_TYPES;
    }

    private static List<DailyQuizProblemType> validateTargetTypes(
            List<DailyQuizProblemType> targetTypes
    ) {
        if (targetTypes.size() != TARGET_QUESTION_COUNT) {
            throw new IllegalStateException(
                    "문항 유형 분배 수는 목표 문항 수와 일치해야 합니다."
            );
        }
        return List.copyOf(targetTypes);
    }
}
