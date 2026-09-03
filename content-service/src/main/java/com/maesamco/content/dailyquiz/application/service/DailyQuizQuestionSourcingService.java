package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationResult;
import com.maesamco.content.dailyquiz.application.generation.GeneratedDailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyQuizQuestionSourcingService {

    // 문제 은행에서 재사용 문항 선정
    private final DailyQuizQuestionReuseService reuseService;
    // 부족한 개념 문제를 AI로 생성
    private final DailyQuizQuestionGenerationService generationService;
    // 생성된 문항을 문제은행에 저장
    private final DailyQuizQuestionRepository questionRepository;

    public DailyQuizQuestionSourcingResult sourceQuestions(List<String> requiredConcepts) {
        QuestionSelection selection = reuseService.selectReusableQuestions(requiredConcepts);

        DailyQuizQuestionGenerationResult generationResult =
                generationService.generateMissingQuestions(selection.missingConcepts());

        List<DailyQuizQuestion> savedGeneratedQuestions = new ArrayList<>();
        List<String> failedConcepts = new ArrayList<>(generationResult.failedConcepts());

        for (GeneratedDailyQuizQuestion generatedQuestion : generationResult.generatedQuestions()) {
            DailyQuizQuestion question;
            try {
                question = toDailyQuizQuestion(generatedQuestion);
            } catch (BusinessException exception) {
                // 도메인 규칙을 통과하지 못한 AI 문항만 실패 처리하고 다음 문항 생성을 계속합니다.
                failedConcepts.addAll(generatedQuestion.conceptTags());
                continue;
            }

            DailyQuizQuestion savedQuestion = questionRepository.save(question);

            savedGeneratedQuestions.add(savedQuestion);
        }

        List<DailyQuizQuestion> sourcedQuestions = new ArrayList<>(selection.selectedQuestions());

        sourcedQuestions.addAll(savedGeneratedQuestions);

        return new DailyQuizQuestionSourcingResult(sourcedQuestions, failedConcepts);
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
