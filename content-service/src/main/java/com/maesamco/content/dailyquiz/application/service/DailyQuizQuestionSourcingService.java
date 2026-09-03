package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationResult;
import com.maesamco.content.dailyquiz.application.generation.GeneratedDailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyQuizQuestionSourcingService {

    // 문제 은행에서 재사용 문항 선정
    private final DailyQuizQuestionReuseService reuseService;
    // 부족한 개념 문제를 AI로 생성
    private final DailyQuizQuestionGenerationService generationService;
    // 생성된 문항을 문제은행에 저장
    private final DailyQuizQuestionRepository questionRepository;

    public DailyQuizQuestionSourcingResult sourceQuestions(ConceptSlots requiredConcepts) {
        if (requiredConcepts == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "개념 슬롯은 필수입니다.");
        }

        QuestionSelection selection = reuseService.selectReusableQuestions(requiredConcepts.values());

        DailyQuizQuestionGenerationResult generationResult =
                generationService.generateMissingQuestions(selection.missingConceptsBySlot());

        Map<Integer, DailyQuizQuestion> questionsBySlot =
                new HashMap<>(selection.selectedQuestionsBySlot());
        Map<Integer, String> failedConceptsBySlot =
                new HashMap<>(generationResult.failedConceptsBySlot());

        for (Map.Entry<Integer, GeneratedDailyQuizQuestion> generatedSlot
                : generationResult.generatedQuestionsBySlot().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            int slotIndex = generatedSlot.getKey();
            GeneratedDailyQuizQuestion generatedQuestion = generatedSlot.getValue();
            DailyQuizQuestion question;
            try {
                question = toDailyQuizQuestion(generatedQuestion);
            } catch (BusinessException exception) {
                // 도메인 규칙을 통과하지 못한 AI 문항만 실패 처리하고 다음 문항 생성을 계속합니다.
                log.warn(
                        "AI 생성 Daily Quiz 문항 도메인 검증 실패. conceptTags={}, reason={}",
                        generatedQuestion.conceptTags(),
                        exception.getMessage()
                );
                failedConceptsBySlot.put(slotIndex, requiredConcepts.at(slotIndex));
                continue;
            }

            DailyQuizQuestion savedQuestion = questionRepository.save(question);

            questionsBySlot.put(slotIndex, savedQuestion);
        }

        List<DailyQuizQuestion> sourcedQuestions = valuesInSlotOrder(
                questionsBySlot,
                requiredConcepts.size()
        );
        List<String> failedConcepts = valuesInSlotOrder(
                failedConceptsBySlot,
                requiredConcepts.size()
        );

        return new DailyQuizQuestionSourcingResult(sourcedQuestions, failedConcepts);
    }

    private static <T> List<T> valuesInSlotOrder(Map<Integer, T> valuesBySlot, int slotCount) {
        return IntStream.range(0, slotCount)
                .mapToObj(valuesBySlot::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static DailyQuizQuestion toDailyQuizQuestion(GeneratedDailyQuizQuestion generatedQuestion) {
        return DailyQuizQuestion.createNew(
                generatedQuestion.problemType(),
                generatedQuestion.questionText(),
                generatedQuestion.choices(),
                generatedQuestion.answer(),
                generatedQuestion.allowedAnswerVariants(),
                generatedQuestion.conceptTags()
        );
    }
}
