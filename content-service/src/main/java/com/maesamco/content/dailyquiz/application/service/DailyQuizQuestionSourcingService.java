package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.application.generation.DailyQuizQuestionGenerationResult;
import com.maesamco.content.dailyquiz.application.generation.GeneratedDailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
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

        for (GeneratedDailyQuizQuestion generatedQuestion : generationResult.generatedQuestions()) {
            DailyQuizQuestion question = toDailyQuizQuestion(generatedQuestion);

            DailyQuizQuestion savedQuestion = questionRepository.save(question);

            savedGeneratedQuestions.add(savedQuestion);
        }

        List<DailyQuizQuestion> sourcedQuestions = new ArrayList<>(selection.selectedQuestions());

        sourcedQuestions.addAll(savedGeneratedQuestions);

        return new DailyQuizQuestionSourcingResult(sourcedQuestions, generationResult.failedConcepts());
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

