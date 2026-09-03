package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

        List<DailyQuizQuestion> selectedQuestions = new ArrayList<>();
        List<String> missingConcepts = new ArrayList<>();

        for (int slotIndex = 0; slotIndex < requiredConcepts.size(); slotIndex++) {
            DailyQuizQuestion selectedQuestion = questionBySlot[slotIndex];

            if (selectedQuestion != null) {
                selectedQuestions.add(selectedQuestion);
            } else {
                missingConcepts.add(requiredConcepts.get(slotIndex));
            }
        }

        return new QuestionSelection(selectedQuestions, missingConcepts);
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
