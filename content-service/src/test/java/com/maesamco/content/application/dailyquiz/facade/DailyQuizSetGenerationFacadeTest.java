package com.maesamco.content.application.dailyquiz.facade;

import com.maesamco.content.application.dailyquiz.command.DailyQuizSetGenerationCommand;
import com.maesamco.content.application.dailyquiz.persistence_service.DailyQuizSetPersistenceService;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSetGenerationResult;
import com.maesamco.content.application.dailyquiz.service.DailyQuizConceptSlotSelector;
import com.maesamco.content.application.dailyquiz.service.DailyQuizQuestionSlotAllocator;
import com.maesamco.content.domain.dailyquiz.DailyQuizConceptCandidates;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyQuizSetGenerationFacadeTest {

    @Test
    void 이미_생성된_날짜의_퀴즈는_문항_확보_없이_건너뛴다() {
        DailyQuizAttemptRepository attemptRepository = mock(DailyQuizAttemptRepository.class);
        DailyQuizConceptSlotSelector conceptSlotSelector = mock(DailyQuizConceptSlotSelector.class);
        DailyQuizQuestionSlotAllocator questionSlotAllocator = mock(DailyQuizQuestionSlotAllocator.class);
        DailyQuizQuestionSourcingFacade sourcingFacade = mock(DailyQuizQuestionSourcingFacade.class);
        DailyQuizSetPersistenceService persistenceService = mock(DailyQuizSetPersistenceService.class);
        DailyQuizSetGenerationFacade facade = new DailyQuizSetGenerationFacade(
                attemptRepository, conceptSlotSelector, questionSlotAllocator, sourcingFacade, persistenceService
        );
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        when(attemptRepository.existsByUserIdAndAttemptDate(userId, attemptDate)).thenReturn(true);

        DailyQuizSetGenerationResult result = facade.generate(DailyQuizSetGenerationCommand.from(
                userId, attemptDate, DailyQuizConceptCandidates.fromInterests(List.of("반복문"))
        ));

        assertThat(result).isEqualTo(DailyQuizSetGenerationResult.alreadyExists());
        verifyNoInteractions(conceptSlotSelector, questionSlotAllocator, sourcingFacade, persistenceService);
    }
}
