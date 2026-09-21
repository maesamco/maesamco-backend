package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.aigeneration.AiGenerationHistoryRecorder;
import com.maesamco.content.domain.aigeneration.entity.AiGenerationPurpose;
import com.maesamco.content.application.dailyquiz.generation.DailyQuizQuestionGenerationException;
import com.maesamco.content.application.dailyquiz.generation.DailyQuizQuestionGenerationResult;
import com.maesamco.content.application.dailyquiz.generation.DailyQuizQuestionGenerator;
import com.maesamco.content.application.dailyquiz.generation.GeneratedDailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
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
    private final AiGenerationHistoryRecorder historyRecorder;

    public DailyQuizQuestionGenerationResult generateMissingQuestions(
            Map<Integer, QuestionSlot> missingQuestionSlotsByIndex
    ) {
        Map<Integer, GeneratedDailyQuizQuestion> generatedQuestionsBySlot = new LinkedHashMap<>();
        Map<Integer, String> failedConceptsBySlot = new LinkedHashMap<>();

        for (Map.Entry<Integer, QuestionSlot> missingSlot : missingQuestionSlotsByIndex.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            int slotIndex = missingSlot.getKey();
            QuestionSlot questionSlot = missingSlot.getValue();
            String concept = questionSlot.conceptTag();
            long startedAtNanos = System.nanoTime();

            // 슬롯 하나에 대해 AI를 호출하고 구조화 응답 검증까지 성공한 문항만 결과에 담습니다.
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
                // 외부 AI 호출 또는 응답 변환·검증 실패를 기록하고 다음 슬롯 생성을 계속합니다.
                log.warn(
                        "Daily Quiz AI 문항 생성 실패. slotIndex={}, conceptTag={}, elapsedMs={}",
                        slotIndex,
                        concept,
                        exception.getGenerationMetadata().responseTimeMs(),
                        exception
                );
                failedConceptsBySlot.put(slotIndex, concept);

                historyRecorder.recordFailure(
                        AiGenerationPurpose.DAILY_QUIZ_GENERATION,
                        Map.of("conceptTag", concept),
                        exception.getGenerationMetadata(),
                        exception
                );
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
