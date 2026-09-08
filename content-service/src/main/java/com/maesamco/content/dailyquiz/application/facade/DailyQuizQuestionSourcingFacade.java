package com.maesamco.content.dailyquiz.application.facade;

import com.maesamco.content.aigeneration.application.AiGenerationHistoryRecorder;
import com.maesamco.content.aigeneration.domain.entity.AiGenerationPurpose;
import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationResult;
import com.maesamco.content.dailyquiz.application.generation.GeneratedDailyQuizQuestion;
import com.maesamco.content.dailyquiz.application.service.ConceptSlots;
import com.maesamco.content.dailyquiz.application.service.DailyQuizQuestionGenerationService;
import com.maesamco.content.dailyquiz.application.service.DailyQuizQuestionReuseService;
import com.maesamco.content.dailyquiz.application.service.DailyQuizQuestionSourcingResult;
import com.maesamco.content.dailyquiz.application.service.QuestionSelection;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.maesamco.content.global.util.DataIntegrityViolations.isUniqueViolation;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyQuizQuestionSourcingFacade {

    // 문제 은행에서 재사용 문항 선정
    private final DailyQuizQuestionReuseService reuseService;
    // 부족한 개념 문제를 AI로 생성
    private final DailyQuizQuestionGenerationService generationService;
    // 생성된 문항을 문제은행에 저장
    private final DailyQuizQuestionRepository questionRepository;

    private final AiGenerationHistoryRecorder historyRecorder;

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

            // AI 응답을 DailyQuizQuestion 도메인 객체로 변환하면서 도메인 규칙을 검증합니다.
            try {
                question = toDailyQuizQuestion(generatedQuestion);
            } catch (BusinessException exception) {
                // 도메인 규칙을 통과하지 못한 AI 문항을 실패로 기록하고 다음 슬롯을 처리합니다.
                historyRecorder.recordFailure(
                        AiGenerationPurpose.DAILY_QUIZ_GENERATION,
                        Map.of("conceptTag", requiredConcepts.at(slotIndex)),
                        generatedQuestion.generationMetadata(),
                        exception
                );

                // 도메인 규칙을 통과하지 못한 AI 문항만 실패 처리하고 다음 문항 생성을 계속합니다.
                log.warn(
                        "AI 생성 Daily Quiz 문항 도메인 검증 실패. conceptTags={}, reason={}",
                        generatedQuestion.conceptTags(),
                        exception.getMessage()
                );
                failedConceptsBySlot.put(slotIndex, requiredConcepts.at(slotIndex));
                continue;
            }

            // 도메인 검증을 통과한 문항을 저장하고, 발급된 문항 ID와 함께 성공 이력을 기록합니다.
            try {
                DailyQuizQuestion savedQuestion = questionRepository.save(question);
                questionsBySlot.put(slotIndex, savedQuestion);

                historyRecorder.recordSuccess(
                        AiGenerationPurpose.DAILY_QUIZ_GENERATION,
                        savedQuestion.getId(),
                        Map.of("conceptTag", requiredConcepts.at(slotIndex)),
                        generatedQuestion.generationMetadata()
                );
            } catch (DataIntegrityViolationException exception) {
                // UNIQUE를 포함한 DB 무결성 오류를 실패로 기록합니다.
                historyRecorder.recordFailure(
                        AiGenerationPurpose.DAILY_QUIZ_GENERATION,
                        Map.of("conceptTag", requiredConcepts.at(slotIndex)),
                        generatedQuestion.generationMetadata(),
                        exception
                );

                if (!isUniqueViolation(exception)) {
                    // 도메인 검증과 DB 제약의 불일치 가능성이 있으므로 시스템 오류로 전파합니다.
                    log.error(
                            "AI 생성 Daily Quiz 문항 저장 중 예상하지 못한 무결성 오류. "
                                    + "slotIndex={}, conceptTags={}",
                            slotIndex,
                            generatedQuestion.conceptTags(),
                            exception
                    );
                    throw exception;
                }

                // 문항 하나의 UNIQUE 충돌은 해당 슬롯만 실패 처리하고 나머지 저장을 계속합니다.
                log.warn(
                        "AI 생성 Daily Quiz 문항 UNIQUE 충돌. slotIndex={}, conceptTags={}, reason={}",
                        slotIndex,
                        generatedQuestion.conceptTags(),
                        exception.getMessage()
                );
                failedConceptsBySlot.put(slotIndex, requiredConcepts.at(slotIndex));
            } catch (RuntimeException exception) {
                historyRecorder.recordFailure(
                        AiGenerationPurpose.DAILY_QUIZ_GENERATION,
                        Map.of("conceptTag", requiredConcepts.at(slotIndex)),
                        generatedQuestion.generationMetadata(),
                        exception
                );
                throw exception;
            }
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
