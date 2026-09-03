package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationException;
import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationResult;
import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerator;
import com.maesamco.content.dailyquiz.application.generation.GeneratedDailyQuizQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyQuizQuestionGenerationService {

    private final DailyQuizQuestionGenerator questionGenerator;

    public DailyQuizQuestionGenerationResult generateMissingQuestions(
            Map<Integer, String> missingConceptsBySlot
    ) {
        Map<Integer, GeneratedDailyQuizQuestion> generatedQuestionsBySlot = new LinkedHashMap<>();
        Map<Integer, String> failedConceptsBySlot = new LinkedHashMap<>();

        for (Map.Entry<Integer, String> missingSlot : missingConceptsBySlot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            int slotIndex = missingSlot.getKey();
            String concept = missingSlot.getValue();
            long startedAtNanos = System.nanoTime();
            try {
                GeneratedDailyQuizQuestion generatedQuestion = questionGenerator.generate(concept);

                generatedQuestionsBySlot.put(slotIndex, generatedQuestion);
                log.info(
                        "Daily Quiz AI 문항 생성 성공. slotIndex={}, conceptTag={}, elapsedMs={}",
                        slotIndex,
                        concept,
                        elapsedMillis(startedAtNanos)
                );
            } catch (DailyQuizQuestionGenerationException exception) {
                log.warn(
                        "Daily Quiz AI 문항 생성 실패. slotIndex={}, conceptTag={}, elapsedMs={}",
                        slotIndex,
                        concept,
                        elapsedMillis(startedAtNanos),
                        exception
                );
                failedConceptsBySlot.put(slotIndex, concept);
            }
        }

        return new DailyQuizQuestionGenerationResult(
                generatedQuestionsBySlot,
                failedConceptsBySlot
        );
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
