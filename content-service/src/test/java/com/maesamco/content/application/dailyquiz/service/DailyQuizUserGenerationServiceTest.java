package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.exception.DailyQuizUserProcessingException;
import com.maesamco.content.application.dailyquiz.facade.DailyQuizSetGenerationFacade;
import com.maesamco.content.application.dailyquiz.query_service.DailyQuizConceptCandidateQueryService;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSetGenerationResult;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizAttemptRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyQuizUserGenerationServiceTest {

    private final DailyQuizConceptCandidateQueryService candidateQueryService =
            mock(DailyQuizConceptCandidateQueryService.class);
    private final DailyQuizSetGenerationFacade setGenerationFacade =
            mock(DailyQuizSetGenerationFacade.class);
    private final DailyQuizAttemptRepository attemptRepository = mock(DailyQuizAttemptRepository.class);
    private final DailyQuizUserGenerationService service =
            new DailyQuizUserGenerationService(candidateQueryService, setGenerationFacade, attemptRepository);

    @Test
    void 이미_생성된_날짜는_개념_조회_전에_건너뛴다() {
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        when(attemptRepository.existsByUserIdAndAttemptDate(userId, attemptDate)).thenReturn(true);

        assertThat(service.generate(userId, attemptDate))
                .isEqualTo(DailyQuizSetGenerationResult.alreadyExists());
        verifyNoInteractions(candidateQueryService, setGenerationFacade);
    }

    @Test
    void 개념_후보_데이터_오류는_사용자_단위_예외로_분류한다() {
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        BusinessException cause = new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        when(candidateQueryService.get(any())).thenThrow(cause);

        assertThatThrownBy(() -> service.generate(userId, attemptDate))
                .isInstanceOf(DailyQuizUserProcessingException.class)
                .hasCause(cause);
        verifyNoInteractions(setGenerationFacade);
    }

    @Test
    void 외부_서비스_장애는_사용자_데이터_오류로_바꾸지_않는다() {
        UUID userId = UUID.randomUUID();
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        BusinessException cause = new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR);
        when(candidateQueryService.get(any())).thenThrow(cause);

        assertThatThrownBy(() -> service.generate(userId, attemptDate)).isSameAs(cause);
        verifyNoInteractions(setGenerationFacade);
    }
}
