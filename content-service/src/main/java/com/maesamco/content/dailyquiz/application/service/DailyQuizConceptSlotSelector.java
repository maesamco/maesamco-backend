package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.domain.ConceptSlots;
import com.maesamco.content.dailyquiz.domain.DailyQuizConceptCandidates;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.TARGET_QUESTION_COUNT;


/**
 * 풀이 이력 또는 관심 개념을 이용해 Daily Quiz의 개념 슬롯 5개를 선정합니다.
 */
@Component
public class DailyQuizConceptSlotSelector {

    public Optional<ConceptSlots> select(DailyQuizConceptCandidates candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("개념 선정 후보는 필수입니다.");
        }

        if (candidates.hasProblemProgress()) {
            return selectFromProblemProgress(candidates.wrongConcepts(), candidates.solvedConcepts());
        }
        return selectFromInterests(candidates.interestConcepts());
    }

    private Optional<ConceptSlots> selectFromProblemProgress(List<String> wrongConcepts, List<String> solvedConcepts) {
        List<String> normalizedWrongConcepts = normalizeDistinct(wrongConcepts);
        List<String> normalizedSolvedConcepts = normalizeDistinct(solvedConcepts);

        List<String> slots = new ArrayList<>(TARGET_QUESTION_COUNT);
        Set<String> selectedConcepts = new LinkedHashSet<>();

        // 개념 배치하는제 worng 먼저
        appendDistinct(slots, selectedConcepts, normalizedWrongConcepts);
        appendDistinct(slots, selectedConcepts, normalizedSolvedConcepts);

        // worng이 비어있으면 solved반복 wrong이 있으면 wrong반복
        List<String> repeatCandidates = normalizedWrongConcepts.isEmpty()
                ? normalizedSolvedConcepts
                : normalizedWrongConcepts;

        return fillRemainingSlots(slots, repeatCandidates);
    }

    private Optional<ConceptSlots> selectFromInterests(List<String> interestConcepts) {
        List<String> normalizedInterests = normalizeDistinct(interestConcepts);

        List<String> slots = new ArrayList<>(TARGET_QUESTION_COUNT);
        Set<String> selectedConcepts = new LinkedHashSet<>();

        // 신규 사용자는 서로 다른 관심 개념을 먼저 배치합니다.
        appendDistinct(slots, selectedConcepts, normalizedInterests);

        // 부족한 슬롯은 관심 개념을 순서대로 반복해 채웁니다.
        return fillRemainingSlots(slots, normalizedInterests);
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
