package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.domain.dailyquiz.ConceptSlots;
import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.maesamco.content.domain.dailyquiz.DailyQuizPolicy.TARGET_QUESTION_COUNT;


/**
 * 풀이 이력 또는 관심 개념을 이용해 Daily Quiz의 개념 슬롯 5개를 선정합니다.
 */
@Component
public class DailyQuizConceptSlotSelector {

    public Optional<ConceptSlots> select(DailyQuizConceptCandidates candidates) {
        if (candidates == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "개념 선정 후보는 필수입니다."
            );
        }

        return selectConcepts(
                candidates.wrongConcepts(),
                candidates.correctConcepts(),
                candidates.interestConcepts()
        );
    }

    private Optional<ConceptSlots> selectConcepts(
            List<String> wrongConcepts,
            List<String> correctConcepts,
            List<String> interestConcepts
    ) {
        List<String> normalizedWrongConcepts = normalizeDistinct(wrongConcepts);
        List<String> normalizedCorrectConcepts = normalizeDistinct(correctConcepts);
        List<String> normalizedInterests = normalizeDistinct(interestConcepts);

        List<String> slots = new ArrayList<>(TARGET_QUESTION_COUNT);
        Set<String> selectedConcepts = new LinkedHashSet<>();

        // 서로 다른 오답 개념을 먼저 배치한 뒤 정답 개념을 배치합니다.
        appendDistinct(slots, selectedConcepts, normalizedWrongConcepts);
        appendDistinct(slots, selectedConcepts, normalizedCorrectConcepts);
        appendDistinct(slots, selectedConcepts, normalizedInterests);

        List<String> repeatCandidates = !normalizedWrongConcepts.isEmpty()
                ? normalizedWrongConcepts
                : !normalizedCorrectConcepts.isEmpty() ? normalizedCorrectConcepts : normalizedInterests;

        return fillRemainingSlots(slots, repeatCandidates);
    }

    private static List<String> normalizeDistinct(List<String> concepts) {
        return concepts.stream()
                .map(String::strip)
                .distinct()
                .toList();
    }

    private static void appendDistinct(List<String> slots, Set<String> selectedConcepts, List<String> candidates) {
        for (String concept : candidates) {
            if (slots.size() == TARGET_QUESTION_COUNT) {
                return;
            }

            if (selectedConcepts.add(concept)) {
                slots.add(concept);
            }
        }
    }

    private static Optional<ConceptSlots> fillRemainingSlots(List<String> slots, List<String> repeatConcepts) {
        if (repeatConcepts.isEmpty()) {
            return Optional.empty();
        }

        int repeatIndex = 0;

        while (slots.size() < TARGET_QUESTION_COUNT) {
            String concept = repeatConcepts.get(repeatIndex % repeatConcepts.size());
            slots.add(concept);
            repeatIndex++;
        }
        return Optional.of(new ConceptSlots(slots));
    }
}
