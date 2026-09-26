package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.domain.dailyquiz.DailyQuizQuestionTypePolicy;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.maesamco.content.domain.dailyquiz.DailyQuizPolicy.TARGET_QUESTION_COUNT;

@Component
@RequiredArgsConstructor
public class DailyQuizFallbackQuestionSelector {

    private final DailyQuizQuestionRepository questionRepository;

    public List<DailyQuizQuestion> fill(
            UUID userId,
            LocalDate attemptDate,
            List<DailyQuizQuestion> selectedQuestions
    ) {
        if (selectedQuestions.size() >= TARGET_QUESTION_COUNT) {
            return List.copyOf(selectedQuestions);
        }

        List<DailyQuizQuestion> result = new ArrayList<>(selectedQuestions);
        Set<UUID> assignedIds = new HashSet<>();
        Map<DailyQuizProblemType, Integer> counts = new EnumMap<>(DailyQuizProblemType.class);
        Map<DailyQuizProblemType, Integer> targetCounts = new EnumMap<>(DailyQuizProblemType.class);
        selectedQuestions.forEach(question -> {
            assignedIds.add(question.getId());
            counts.merge(question.getProblemType(), 1, Integer::sum);
        });
        DailyQuizQuestionTypePolicy.targetTypes()
                .forEach(type -> targetCounts.merge(type, 1, Integer::sum));

        List<DailyQuizQuestion> candidates = questionRepository.findActiveFallbackQuestions().stream()
                .sorted(Comparator
                        .comparingInt((DailyQuizQuestion question) -> Objects.hash(userId, attemptDate, question.getId()))
                        .thenComparing(DailyQuizQuestion::getId))
                .toList();

        for (DailyQuizProblemType type : DailyQuizQuestionTypePolicy.targetTypes()) {
            if (result.size() == TARGET_QUESTION_COUNT) {
                break;
            }
            if (counts.getOrDefault(type, 0) >= targetCounts.get(type)) {
                continue;
            }
            candidates.stream()
                    .filter(question -> question.getProblemType() == type)
                    .filter(question -> !assignedIds.contains(question.getId()))
                    .findFirst()
                    .ifPresent(question -> add(result, assignedIds, counts, question));
        }

        for (DailyQuizQuestion question : candidates) {
            if (result.size() == TARGET_QUESTION_COUNT) {
                break;
            }
            if (!assignedIds.contains(question.getId())) {
                add(result, assignedIds, counts, question);
            }
        }

        return List.copyOf(result);
    }

    private static void add(
            List<DailyQuizQuestion> result,
            Set<UUID> assignedIds,
            Map<DailyQuizProblemType, Integer> counts,
            DailyQuizQuestion question
    ) {
        result.add(question);
        assignedIds.add(question.getId());
        counts.merge(question.getProblemType(), 1, Integer::sum);
    }
}
