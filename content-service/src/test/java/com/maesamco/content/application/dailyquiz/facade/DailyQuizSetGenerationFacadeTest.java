package com.maesamco.content.application.dailyquiz.facade;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSetGenerationCommand;
import com.maesamco.content.application.dailyquiz.command.DailyQuizSetCreateCommand;
import com.maesamco.content.application.dailyquiz.persistence_service.DailyQuizSetPersistenceService;
import com.maesamco.content.application.dailyquiz.query_service.DailyQuizConceptCandidateQueryService;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSetGenerationResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizQuestionSourcingResult;
import com.maesamco.content.application.dailyquiz.service.DailyQuizConceptSlotSelector;
import com.maesamco.content.application.dailyquiz.service.DailyQuizFallbackQuestionSelector;
import com.maesamco.content.application.dailyquiz.service.DailyQuizQuestionSlotAllocator;
import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import com.maesamco.content.domain.dailyquiz.ConceptSlots;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizProblemType;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyQuizSetGenerationFacadeTest {

    @Test
    void 이미_생성된_날짜의_퀴즈는_문항_확보_없이_건너뛴다() {
        DailyQuizAttemptRepository attemptRepository = mock(DailyQuizAttemptRepository.class);
        DailyQuizConceptSlotSelector conceptSlotSelector = mock(DailyQuizConceptSlotSelector.class);
        DailyQuizConceptCandidateQueryService candidateQueryService = mock(DailyQuizConceptCandidateQueryService.class);
        DailyQuizQuestionSlotAllocator questionSlotAllocator = mock(DailyQuizQuestionSlotAllocator.class);
        DailyQuizQuestionSourcingFacade sourcingFacade = mock(DailyQuizQuestionSourcingFacade.class);
        DailyQuizFallbackQuestionSelector fallbackSelector = mock(DailyQuizFallbackQuestionSelector.class);
        DailyQuizSetPersistenceService persistenceService = mock(DailyQuizSetPersistenceService.class);
        DailyQuizSetGenerationFacade facade = new DailyQuizSetGenerationFacade(
                attemptRepository, conceptSlotSelector, candidateQueryService,
                questionSlotAllocator, sourcingFacade,
                fallbackSelector, persistenceService
        );
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        when(attemptRepository.existsByUserIdAndAttemptDate(userId, attemptDate)).thenReturn(true);

        DailyQuizSetGenerationResult result = facade.generate(DailyQuizSetGenerationCommand.from(
                userId, attemptDate, DailyQuizConceptCandidates.fromInterests(List.of("반복문"))
        ));

        assertThat(result).isEqualTo(DailyQuizSetGenerationResult.alreadyExists());
        verifyNoInteractions(conceptSlotSelector, questionSlotAllocator, sourcingFacade,
                fallbackSelector, persistenceService);
    }

    @Test
    void 개념이_없어도_공통_문제_다섯_개로_세트를_생성한다() {
        DailyQuizAttemptRepository attemptRepository = mock(DailyQuizAttemptRepository.class);
        DailyQuizConceptSlotSelector conceptSlotSelector = mock(DailyQuizConceptSlotSelector.class);
        DailyQuizConceptCandidateQueryService candidateQueryService = mock(DailyQuizConceptCandidateQueryService.class);
        DailyQuizQuestionSlotAllocator questionSlotAllocator = mock(DailyQuizQuestionSlotAllocator.class);
        DailyQuizQuestionSourcingFacade sourcingFacade = mock(DailyQuizQuestionSourcingFacade.class);
        DailyQuizFallbackQuestionSelector fallbackSelector = mock(DailyQuizFallbackQuestionSelector.class);
        DailyQuizSetPersistenceService persistenceService = mock(DailyQuizSetPersistenceService.class);
        DailyQuizSetGenerationFacade facade = new DailyQuizSetGenerationFacade(
                attemptRepository, conceptSlotSelector, candidateQueryService,
                questionSlotAllocator, sourcingFacade,
                fallbackSelector, persistenceService
        );
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 26);
        List<DailyQuizQuestion> fallbackQuestions = List.of(
                question(), question(), question(), question(), question()
        );
        when(conceptSlotSelector.select(any(), any(), any())).thenReturn(Optional.empty());
        when(fallbackSelector.fill(userId, attemptDate, List.of())).thenReturn(fallbackQuestions);
        UUID attemptId = UUID.randomUUID();
        when(persistenceService.create(any())).thenReturn(DailyQuizSetGenerationResult.created(attemptId, 5));

        DailyQuizSetGenerationResult result = facade.generate(DailyQuizSetGenerationCommand.from(
                userId, attemptDate, DailyQuizConceptCandidates.fromInterests(List.of())
        ));

        assertThat(result).isEqualTo(DailyQuizSetGenerationResult.created(attemptId, 5));
        ArgumentCaptor<DailyQuizSetCreateCommand> command = ArgumentCaptor.forClass(DailyQuizSetCreateCommand.class);
        verify(persistenceService).create(command.capture());
        assertThat(command.getValue().questionIds()).hasSize(5).doesNotHaveDuplicates();
        verifyNoInteractions(questionSlotAllocator, sourcingFacade);
    }

    @Test
    void 공통_문제까지_부족하면_세_문항은_제공하고_두_문항은_미노출한다() {
        DailyQuizAttemptRepository attemptRepository = mock(DailyQuizAttemptRepository.class);
        DailyQuizConceptSlotSelector conceptSlotSelector = mock(DailyQuizConceptSlotSelector.class);
        DailyQuizConceptCandidateQueryService candidateQueryService = mock(DailyQuizConceptCandidateQueryService.class);
        DailyQuizQuestionSlotAllocator questionSlotAllocator = mock(DailyQuizQuestionSlotAllocator.class);
        DailyQuizQuestionSourcingFacade sourcingFacade = mock(DailyQuizQuestionSourcingFacade.class);
        DailyQuizFallbackQuestionSelector fallbackSelector = mock(DailyQuizFallbackQuestionSelector.class);
        DailyQuizSetPersistenceService persistenceService = mock(DailyQuizSetPersistenceService.class);
        DailyQuizSetGenerationFacade facade = new DailyQuizSetGenerationFacade(
                attemptRepository, conceptSlotSelector, candidateQueryService,
                questionSlotAllocator, sourcingFacade,
                fallbackSelector, persistenceService
        );
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 26);
        List<DailyQuizQuestion> threeQuestions = List.of(question(), question(), question());
        List<DailyQuizQuestion> twoQuestions = List.of(question(), question());
        when(conceptSlotSelector.select(any(), any(), any())).thenReturn(Optional.empty());
        when(fallbackSelector.fill(eq(userId), eq(attemptDate), eq(List.of())))
                .thenReturn(threeQuestions)
                .thenReturn(twoQuestions);
        when(persistenceService.create(any())).thenReturn(DailyQuizSetGenerationResult.created(UUID.randomUUID(), 3));
        DailyQuizSetGenerationCommand command = DailyQuizSetGenerationCommand.from(
                userId, attemptDate, DailyQuizConceptCandidates.fromInterests(List.of())
        );

        assertThat(facade.generate(command).questionCount()).isEqualTo(3);
        assertThat(facade.generate(command)).isEqualTo(DailyQuizSetGenerationResult.insufficientQuestions(2));
        verify(persistenceService, times(1)).create(any());
    }

    @Test
    void 풀이_개념이_다섯_개라도_문항_확보가_부족하면_관심_개념을_다시_시도한다() {
        DailyQuizAttemptRepository attemptRepository = mock(DailyQuizAttemptRepository.class);
        DailyQuizConceptSlotSelector conceptSlotSelector = mock(DailyQuizConceptSlotSelector.class);
        DailyQuizConceptCandidateQueryService candidateQueryService = mock(DailyQuizConceptCandidateQueryService.class);
        DailyQuizQuestionSlotAllocator questionSlotAllocator = new DailyQuizQuestionSlotAllocator();
        DailyQuizQuestionSourcingFacade sourcingFacade = mock(DailyQuizQuestionSourcingFacade.class);
        DailyQuizFallbackQuestionSelector fallbackSelector = mock(DailyQuizFallbackQuestionSelector.class);
        DailyQuizSetPersistenceService persistenceService = mock(DailyQuizSetPersistenceService.class);
        DailyQuizSetGenerationFacade facade = new DailyQuizSetGenerationFacade(
                attemptRepository, conceptSlotSelector, candidateQueryService,
                questionSlotAllocator, sourcingFacade, fallbackSelector, persistenceService
        );
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 26);
        ConceptSlots progressSlots = new ConceptSlots(List.of("반복문", "조건문", "변수", "배열", "함수"));
        when(conceptSlotSelector.select(any(), any(), any())).thenReturn(Optional.of(progressSlots));
        when(candidateQueryService.getInterestConceptTags(userId)).thenReturn(List.of("자료구조"));
        List<DailyQuizQuestion> progressQuestions = List.of(
                question(DailyQuizProblemType.MULTIPLE_CHOICE),
                question(DailyQuizProblemType.SHORT_ANSWER),
                question(DailyQuizProblemType.MULTIPLE_CHOICE)
        );
        List<DailyQuizQuestion> interestQuestions = List.of(
                question(DailyQuizProblemType.SHORT_ANSWER),
                question(DailyQuizProblemType.FILL_IN_BLANK)
        );
        when(sourcingFacade.sourceQuestions(any()))
                .thenReturn(new DailyQuizQuestionSourcingResult(progressQuestions, List.of()))
                .thenReturn(new DailyQuizQuestionSourcingResult(interestQuestions, List.of()));
        when(fallbackSelector.fill(eq(userId), eq(attemptDate), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(persistenceService.create(any()))
                .thenReturn(DailyQuizSetGenerationResult.created(UUID.randomUUID(), 5));

        DailyQuizSetGenerationResult result = facade.generate(DailyQuizSetGenerationCommand.from(
                userId, attemptDate, DailyQuizConceptCandidates.fromProblemProgress(
                        progressSlots.values(), List.of()
                )
        ));

        assertThat(result.questionCount()).isEqualTo(5);
        ArgumentCaptor<QuestionSlots> slots = ArgumentCaptor.forClass(QuestionSlots.class);
        verify(sourcingFacade, times(2)).sourceQuestions(slots.capture());
        assertThat(slots.getAllValues().get(1).values())
                .hasSize(2)
                .allSatisfy(slot -> assertThat(slot.conceptTag()).isEqualTo("자료구조"));
        verify(fallbackSelector).fill(eq(userId), eq(attemptDate), argThat(questions -> questions.size() == 5));
    }

    private static DailyQuizQuestion question() {
        DailyQuizQuestion question = mock(DailyQuizQuestion.class);
        when(question.getId()).thenReturn(UUID.randomUUID());
        return question;
    }

    private static DailyQuizQuestion question(DailyQuizProblemType type) {
        DailyQuizQuestion question = question();
        when(question.getProblemType()).thenReturn(type);
        return question;
    }
}
