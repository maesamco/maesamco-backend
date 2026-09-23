package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionSelectionResult;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
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

    public DailyQuizQuestionSelectionResult select(
            QuestionSlots requiredSlots,
            List<DailyQuizQuestion> candidates
    ) {
        // MVP에서는 날짜가 다르면 동일 문항의 반복 출제를 허용하고, 결정적인 선택 결과를 위해 UUID 순으로 정렬합니다.
        // TODO: 이후 최근 3일간 출제된 문항을 제외하거나 출제 빈도가 낮은 문항을 우선하도록 개선합니다.
        List<DailyQuizQuestion> sortedCandidates = candidates.stream()
                .sorted(Comparator.comparing(DailyQuizQuestion::getId))
                .toList();

        Map<UUID, Integer> slotByQuestionId = new HashMap<>();
        DailyQuizQuestion[] questionBySlot = new DailyQuizQuestion[requiredSlots.size()];

        for (int slotIndex = 0; slotIndex < requiredSlots.size(); slotIndex++) {
            tryAssign(
                    slotIndex,
                    requiredSlots,
                    sortedCandidates,
                    slotByQuestionId,
                    questionBySlot,
                    new HashSet<>()
            );
        }

        Map<Integer, DailyQuizQuestion> selectedQuestionsBySlot = new LinkedHashMap<>();
        Map<Integer, QuestionSlot> missingQuestionSlotsByIndex = new LinkedHashMap<>();

        for (int slotIndex = 0; slotIndex < requiredSlots.size(); slotIndex++) {
            DailyQuizQuestion selectedQuestion = questionBySlot[slotIndex];

            if (selectedQuestion != null) {
                selectedQuestionsBySlot.put(slotIndex, selectedQuestion);
            } else {
                missingQuestionSlotsByIndex.put(slotIndex, requiredSlots.at(slotIndex));
            }
        }

        return new DailyQuizQuestionSelectionResult(selectedQuestionsBySlot, missingQuestionSlotsByIndex);
    }

    private boolean tryAssign(
            int slotIndex,
            QuestionSlots requiredSlots,
            List<DailyQuizQuestion> sortedCandidates,
            Map<UUID, Integer> slotByQuestionId,
            DailyQuizQuestion[] questionBySlot,
            Set<UUID> visitedQuestionIds
    ) {
        QuestionSlot requiredSlot = requiredSlots.at(slotIndex);

        for (DailyQuizQuestion candidate : sortedCandidates) {
            if (!candidate.getConceptTags().contains(requiredSlot.conceptTag())
                    || candidate.getProblemType() != requiredSlot.problemType()) {
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
                    requiredSlots,
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
