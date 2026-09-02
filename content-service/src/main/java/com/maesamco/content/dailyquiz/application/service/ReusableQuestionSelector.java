package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReusableQuestionSelector {

    public QuestionSelection select(List<String> requiredConcepts,
                                          List<DailyQuizQuestion> candidates
    ) {
        List<String> remainingConcepts = new ArrayList<>(requiredConcepts);
        List<DailyQuizQuestion>  remainingCandidates = new ArrayList<>(candidates);

        List<DailyQuizQuestion> selectedQuestions = new ArrayList<>();
        List<String> missingConcepts = new ArrayList<>();

        while (!remainingConcepts.isEmpty()) {
            String hardestConcept = findHardestConcept(remainingConcepts, remainingCandidates);
            Optional<DailyQuizQuestion> leastOverlappingCandidate = findLeastOverlappingCandidate(hardestConcept, remainingConcepts, remainingCandidates);

            if (leastOverlappingCandidate.isPresent()) {
                selectedQuestions.add(leastOverlappingCandidate.get());
                remainingCandidates.remove(leastOverlappingCandidate.get());
            } else {
                missingConcepts.add(hardestConcept);
            }
            remainingConcepts.remove(hardestConcept);
        }

        return new QuestionSelection(selectedQuestions, missingConcepts);

    }

    // 요청 개념을 포함하는 퀴즈 수 반환
    private int countCandidates(String requiredConcept, List<DailyQuizQuestion> candidates) {
        // candidates 중 requiredConcept를 포함한 문항 수 반환
        int count = 0;
        for (DailyQuizQuestion candidate : candidates) {
            if (candidate.getConceptTags().contains(requiredConcept)) {
                count++;
            }
        }

        return count;
    }

    // 가장 어려운 즉 문항에 가장 적게 포함된 개념을 반환 - 필요한개념 / 문항들
    private String findHardestConcept(List<String> requiredConcepts, List<DailyQuizQuestion> candidates) {

        String hardestConcept = null;
        int minimumCandidateCount = Integer.MAX_VALUE;

        for (String requiredConcept : requiredConcepts) {
            int count = countCandidates(requiredConcept, candidates);
            if (count < minimumCandidateCount) {
                minimumCandidateCount = count;
                hardestConcept = requiredConcept;
            }

        }

        if (hardestConcept == null) {
            throw new IllegalArgumentException("필요 개념은 하나 이상이어야 합니다.");
        }
        return hardestConcept;

    }

    // 남은 필요 개념과 가장 적게 겹치는 후보 문항 반환 - remainingConcepts 아직 문제 배정을 하지 못한 개념 목록
    private Optional<DailyQuizQuestion> findLeastOverlappingCandidate(
            String requiredConcept,
            List<String> remainingConcepts,
            List<DailyQuizQuestion> candidates) {

        List<DailyQuizQuestion> matchingCandidates = new ArrayList<>();

        // 퀴즈에서 현재 요청된 개념을 가지고 있는 것들을 리스트에 저장
        for (DailyQuizQuestion candidate : candidates) {
            if (candidate.getConceptTags().contains(requiredConcept)) {
                matchingCandidates.add(candidate);
            }
        }

        DailyQuizQuestion leastOverlappingCandidate = null;
        int minimumOverlapCount = Integer.MAX_VALUE;
        for (DailyQuizQuestion question : matchingCandidates) {
            int overlapCount = countConceptOverlaps(question, remainingConcepts);
            if (overlapCount < minimumOverlapCount) {
                minimumOverlapCount = overlapCount;
                leastOverlappingCandidate = question;
            }
        }

        return Optional.ofNullable(leastOverlappingCandidate);
    }

    // 필요한 개념과 후보 문항 한개씩 몇 개 겹치는지
    private int countConceptOverlaps(
            DailyQuizQuestion candidate,
            List<String> remainingConcepts
    ) {
        int count = 0;
        for (String requiredConcept : remainingConcepts) {
            if (candidate.getConceptTags().contains(requiredConcept)) {
                count++;
            }
        }
        return count;
    }
}
