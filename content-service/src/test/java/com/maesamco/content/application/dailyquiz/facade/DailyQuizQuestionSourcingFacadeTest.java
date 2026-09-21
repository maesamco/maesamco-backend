package com.maesamco.content.application.dailyquiz.facade;

import com.maesamco.content.application.aigeneration.AiGenerationHistoryRecorder;
import com.maesamco.content.application.aigeneration.AiGenerationMetadata;
import com.maesamco.content.application.dailyquiz.generation.DailyQuizQuestionGenerationException;
import com.maesamco.content.application.dailyquiz.generation.DailyQuizQuestionGenerator;
import com.maesamco.content.application.dailyquiz.generation.GeneratedDailyQuizQuestion;
import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionSourcingResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionSelectionResult;
import com.maesamco.content.application.dailyquiz.service.DailyQuizQuestionGenerationService;
import com.maesamco.content.application.dailyquiz.service.DailyQuizQuestionReuseService;
import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType.SHORT_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyQuizQuestionSourcingFacadeTest {

    private static final String LOOP = "반복문";
    private static final String CONDITION = "조건문";
    private static final String ARRAY = "배열";
    private static final String STRING = "문자열";
    private static final String METHOD = "메서드";

    @Mock
    private DailyQuizQuestionReuseService reuseService;

    @Mock
    private DailyQuizQuestionGenerator questionGenerator;

    @Mock
    private DailyQuizQuestionRepository questionRepository;

    @Mock
    private AiGenerationHistoryRecorder historyRecorder;

    private DailyQuizQuestionSourcingFacade sourcingFacade;

    @BeforeEach
    void setUp() {
        DailyQuizQuestionGenerationService generationService =
                new DailyQuizQuestionGenerationService(questionGenerator, historyRecorder);
        sourcingFacade = new DailyQuizQuestionSourcingFacade(
                reuseService,
                generationService,
                questionRepository,
                historyRecorder
        );
    }

    @Test
    void 기존_문항으로_모든_슬롯을_채우면_AI를_호출하지_않는다() {
        QuestionSlots questionSlots = questionSlots();
        DailyQuizQuestion loopQuestion = question(1, LOOP);
        DailyQuizQuestion conditionQuestion = question(2, CONDITION);
        DailyQuizQuestion arrayQuestion = question(3, ARRAY);
        DailyQuizQuestion stringQuestion = question(4, STRING);
        DailyQuizQuestion methodQuestion = question(5, METHOD);
        when(reuseService.selectReusableQuestions(questionSlots))
                .thenReturn(new DailyQuizQuestionSelectionResult(
                        Map.of(
                                0, loopQuestion,
                                1, conditionQuestion,
                                2, arrayQuestion,
                                3, stringQuestion,
                                4, methodQuestion
                        ),
                        Map.of()
                ));

        DailyQuizQuestionSourcingResult result = sourcingFacade.sourceQuestions(questionSlots);

        assertThat(result.questions()).containsExactly(
                loopQuestion,
                conditionQuestion,
                arrayQuestion,
                stringQuestion,
                methodQuestion
        );
        assertThat(result.failedConcepts()).isEmpty();
        assertThat(result.canCreateQuiz()).isTrue();
        assertThat(result.isFallback()).isFalse();
        verifyNoInteractions(questionGenerator);
        verify(questionRepository, never()).save(any());
    }

    @Test
    void AI가_일부_문항만_생성해_총_3개를_확보하면_fallback으로_처리한다() {
        QuestionSlots questionSlots = questionSlots();
        DailyQuizQuestion loopQuestion = question(1, LOOP);
        DailyQuizQuestion conditionQuestion = question(2, CONDITION);
        when(reuseService.selectReusableQuestions(questionSlots))
                .thenReturn(new DailyQuizQuestionSelectionResult(
                        Map.of(0, loopQuestion, 1, conditionQuestion),
                        Map.of(
                                2, questionSlots.at(2),
                                3, questionSlots.at(3),
                                4, questionSlots.at(4)
                        )
                ));
        when(questionGenerator.generate(questionSlots.at(2)))
                .thenReturn(generatedQuestion(questionSlots.at(2)));
        when(questionGenerator.generate(questionSlots.at(3))).thenThrow(generationFailure(STRING));
        when(questionGenerator.generate(questionSlots.at(4))).thenThrow(generationFailure(METHOD));
        when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DailyQuizQuestionSourcingResult result = sourcingFacade.sourceQuestions(questionSlots);

        assertThat(result.questions()).hasSize(3);
        assertThat(result.failedConcepts()).containsExactly(STRING, METHOD);
        assertThat(result.canCreateQuiz()).isTrue();
        assertThat(result.isFallback()).isTrue();
        verify(questionRepository, times(1)).save(any());
    }

    @Test
    void 최종_문항이_3개_미만이면_퀴즈를_생성할_수_없다() {
        QuestionSlots questionSlots = questionSlots();
        DailyQuizQuestion loopQuestion = question(1, LOOP);
        when(reuseService.selectReusableQuestions(questionSlots))
                .thenReturn(new DailyQuizQuestionSelectionResult(
                        Map.of(0, loopQuestion),
                        Map.of(
                                1, questionSlots.at(1),
                                2, questionSlots.at(2),
                                3, questionSlots.at(3),
                                4, questionSlots.at(4)
                        )
                ));
        when(questionGenerator.generate(questionSlots.at(1)))
                .thenReturn(generatedQuestion(questionSlots.at(1)));
        when(questionGenerator.generate(questionSlots.at(2))).thenThrow(generationFailure(ARRAY));
        when(questionGenerator.generate(questionSlots.at(3))).thenThrow(generationFailure(STRING));
        when(questionGenerator.generate(questionSlots.at(4))).thenThrow(generationFailure(METHOD));
        when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DailyQuizQuestionSourcingResult result = sourcingFacade.sourceQuestions(questionSlots);

        assertThat(result.questions()).hasSize(2);
        assertThat(result.failedConcepts()).containsExactly(ARRAY, STRING, METHOD);
        assertThat(result.canCreateQuiz()).isFalse();
        assertThat(result.isFallback()).isFalse();
    }

    @Test
    void 한_문항의_도메인_검증이_실패해도_다음_슬롯을_계속_처리한다() {
        QuestionSlots questionSlots = questionSlots();
        DailyQuizQuestion loopQuestion = question(1, LOOP);
        DailyQuizQuestion conditionQuestion = question(2, CONDITION);
        DailyQuizQuestion arrayQuestion = question(3, ARRAY);
        when(reuseService.selectReusableQuestions(questionSlots))
                .thenReturn(new DailyQuizQuestionSelectionResult(
                        Map.of(0, loopQuestion, 1, conditionQuestion, 2, arrayQuestion),
                        Map.of(3, questionSlots.at(3), 4, questionSlots.at(4))
                ));
        when(questionGenerator.generate(questionSlots.at(3)))
                .thenReturn(invalidGeneratedQuestion(questionSlots.at(3)));
        when(questionGenerator.generate(questionSlots.at(4)))
                .thenReturn(generatedQuestion(questionSlots.at(4)));
        when(questionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DailyQuizQuestionSourcingResult result = sourcingFacade.sourceQuestions(questionSlots);

        assertThat(result.questions()).hasSize(4);
        assertThat(result.failedConcepts()).containsExactly(STRING);
        assertThat(result.canCreateQuiz()).isTrue();
        assertThat(result.isFallback()).isTrue();
        verify(questionGenerator).generate(questionSlots.at(4));
        verify(questionRepository, times(1)).save(any());
    }

    @Test
    void 한_문항의_UNIQUE_저장_충돌이_발생해도_다음_슬롯을_계속_처리한다() {
        QuestionSlots questionSlots = questionSlots();
        DailyQuizQuestion loopQuestion = question(1, LOOP);
        DailyQuizQuestion conditionQuestion = question(2, CONDITION);
        DailyQuizQuestion arrayQuestion = question(3, ARRAY);
        when(reuseService.selectReusableQuestions(questionSlots))
                .thenReturn(new DailyQuizQuestionSelectionResult(
                        Map.of(0, loopQuestion, 1, conditionQuestion, 2, arrayQuestion),
                        Map.of(3, questionSlots.at(3), 4, questionSlots.at(4))
                ));
        when(questionGenerator.generate(questionSlots.at(3)))
                .thenReturn(generatedQuestion(questionSlots.at(3)));
        when(questionGenerator.generate(questionSlots.at(4)))
                .thenReturn(generatedQuestion(questionSlots.at(4)));
        DataIntegrityViolationException uniqueViolation = uniqueViolation();
        when(questionRepository.save(any()))
                .thenThrow(uniqueViolation)
                .thenAnswer(invocation -> invocation.getArgument(0));

        DailyQuizQuestionSourcingResult result = sourcingFacade.sourceQuestions(questionSlots);

        assertThat(result.questions()).hasSize(4);
        assertThat(result.failedConcepts()).containsExactly(STRING);
        assertThat(result.canCreateQuiz()).isTrue();
        assertThat(result.isFallback()).isTrue();
        verify(questionGenerator).generate(questionSlots.at(4));
        verify(questionRepository, times(2)).save(any());
    }

    @Test
    void UNIQUE가_아닌_DB_무결성_오류는_상위로_전파한다() {
        QuestionSlots questionSlots = questionSlots();
        DailyQuizQuestion loopQuestion = question(1, LOOP);
        DailyQuizQuestion conditionQuestion = question(2, CONDITION);
        DailyQuizQuestion arrayQuestion = question(3, ARRAY);
        when(reuseService.selectReusableQuestions(questionSlots))
                .thenReturn(new DailyQuizQuestionSelectionResult(
                        Map.of(0, loopQuestion, 1, conditionQuestion, 2, arrayQuestion),
                        Map.of(3, questionSlots.at(3), 4, questionSlots.at(4))
                ));
        when(questionGenerator.generate(questionSlots.at(3)))
                .thenReturn(generatedQuestion(questionSlots.at(3)));
        when(questionGenerator.generate(questionSlots.at(4)))
                .thenReturn(generatedQuestion(questionSlots.at(4)));
        DataIntegrityViolationException unexpectedException =
                new DataIntegrityViolationException("예상하지 못한 테스트 무결성 오류");
        when(questionRepository.save(any())).thenThrow(unexpectedException);

        assertThatThrownBy(() -> sourcingFacade.sourceQuestions(questionSlots))
                .isSameAs(unexpectedException);
        verify(questionRepository, times(1)).save(any());
    }

    private QuestionSlots questionSlots() {
        return new QuestionSlots(List.of(
                new QuestionSlot(LOOP, DailyQuizProblemType.MULTIPLE_CHOICE),
                new QuestionSlot(CONDITION, SHORT_ANSWER),
                new QuestionSlot(ARRAY, DailyQuizProblemType.MULTIPLE_CHOICE),
                new QuestionSlot(STRING, SHORT_ANSWER),
                new QuestionSlot(METHOD, DailyQuizProblemType.FILL_IN_BLANK)
        ));
    }

    private DailyQuizQuestion question(long id, String conceptTag) {
        DailyQuizQuestion question = DailyQuizQuestion.createNew(
                SHORT_ANSWER,
                "테스트 질문 " + id,
                null,
                "정답",
                null,
                List.of(conceptTag)
        );
        ReflectionTestUtils.setField(question, "id", new UUID(0L, id));
        return question;
    }

    private GeneratedDailyQuizQuestion generatedQuestion(QuestionSlot questionSlot) {
        return new GeneratedDailyQuizQuestion(
                questionSlot.problemType(),
                generatedQuestionText(questionSlot),
                generatedChoices(questionSlot),
                generatedAnswer(questionSlot),
                generatedAllowedAnswerVariants(questionSlot),
                List.of(questionSlot.conceptTag()),
                generationMetadata()
        );
    }

    private GeneratedDailyQuizQuestion invalidGeneratedQuestion(QuestionSlot questionSlot) {
        return new GeneratedDailyQuizQuestion(
                questionSlot.problemType(),
                questionSlot.conceptTag() + " 생성 질문",
                null,
                " ",
                null,
                List.of(questionSlot.conceptTag()),
                generationMetadata()
        );
    }

    private String generatedQuestionText(QuestionSlot questionSlot) {
        return questionSlot.problemType() == DailyQuizProblemType.FILL_IN_BLANK
                ? questionSlot.conceptTag() + " ___ 생성 질문"
                : questionSlot.conceptTag() + " 생성 질문";
    }

    private List<String> generatedChoices(QuestionSlot questionSlot) {
        return questionSlot.problemType() == DailyQuizProblemType.MULTIPLE_CHOICE
                ? List.of("정답", "오답1", "오답2", "오답3")
                : null;
    }

    private String generatedAnswer(QuestionSlot questionSlot) {
        return "정답";
    }

    private List<String> generatedAllowedAnswerVariants(QuestionSlot questionSlot) {
        return null;
    }

    private DailyQuizQuestionGenerationException generationFailure(String conceptTag) {
        return new DailyQuizQuestionGenerationException(
                conceptTag,
                generationMetadata(),
                new IllegalStateException("테스트 AI 생성 실패")
        );
    }

    private AiGenerationMetadata generationMetadata() {
        return new AiGenerationMetadata(
                "test-model",
                "v1",
                Instant.parse("2026-09-04T00:00:00Z"),
                100,
                30
        );
    }

    private DataIntegrityViolationException uniqueViolation() {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getKind()).thenReturn(ConstraintViolationException.ConstraintKind.UNIQUE);
        return new DataIntegrityViolationException("테스트 UNIQUE 저장 충돌", cause);
    }
}
