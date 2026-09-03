package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ReusableQuestionSelector {

    public QuestionSelection select(
            List<String> requiredConcepts,
            List<DailyQuizQuestion> candidates
    ) {
        List<DailyQuizQuestion> sortedCandidates = candidates.stream()
                .sorted(Comparator.comparing(DailyQuizQuestion::getId))
                .toList();

        Map<UUID, Integer> slotByQuestionId = new HashMap<>();
        DailyQuizQuestion[] questionBySlot = new DailyQuizQuestion[requiredConcepts.size()];

        for (int slotIndex = 0; slotIndex < requiredConcepts.size(); slotIndex++) {
            tryAssign(
                    slotIndex,
                    requiredConcepts,
                    sortedCandidates,
                    slotByQuestionId,
                    questionBySlot,
                    new HashSet<>()
            );
        }

        Map<Integer, DailyQuizQuestion> selectedQuestionsBySlot = new LinkedHashMap<>();
        Map<Integer, String> missingConceptsBySlot = new LinkedHashMap<>();

        for (int slotIndex = 0; slotIndex < requiredConcepts.size(); slotIndex++) {
            DailyQuizQuestion selectedQuestion = questionBySlot[slotIndex];

            if (selectedQuestion != null) {
                selectedQuestionsBySlot.put(slotIndex, selectedQuestion);
            } else {
                missingConceptsBySlot.put(slotIndex, requiredConcepts.get(slotIndex));
            }
        }

        return new QuestionSelection(selectedQuestionsBySlot, missingConceptsBySlot);
    }

    private boolean tryAssign(
            int slotIndex,
            List<String> requiredConcepts,
            List<DailyQuizQuestion> sortedCandidates,
            Map<UUID, Integer> slotByQuestionId,
            DailyQuizQuestion[] questionBySlot,
            Set<UUID> visitedQuestionIds
    ) {
        String requiredConcept = requiredConcepts.get(slotIndex);

        for (DailyQuizQuestion candidate : sortedCandidates) {
            if (!candidate.getConceptTags().contains(requiredConcept)) {
                continue;
            }

            UUID questionVersionId = candidate.getId();
            if (!visitedQuestionIds.add(questionVersionId)) {
                continue;
            }

            Integer assignedSlotIndex = slotByQuestionId.get(questionVersionId);
            boolean isUnassigned = assignedSlotIndex == null;
            boolean canMoveAssignedSlot = !isUnassigned && tryAssign(
                    assignedSlotIndex,
                    requiredConcepts,
                    sortedCandidates,
                    slotByQuestionId,
                    questionBySlot,
                    visitedQuestionIds
            );

            if (isUnassigned || canMoveAssignedSlot) {
                slotByQuestionId.put(questionVersionId, slotIndex);
                questionBySlot[slotIndex] = candidate;
                return true;
            }
        }

        return false;
    }
}
