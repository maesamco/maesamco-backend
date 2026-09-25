package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.exception.DailyQuizUserProcessingException;
import com.maesamco.content.application.dailyquiz.port.DailyQuizTargetUserPort;
import com.maesamco.content.application.dailyquiz.result.DailyQuizSetGenerationResult;
import com.maesamco.content.application.dailyquiz.result.DailyQuizTargetUserPage;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DailyQuizBatchExecutionServiceTest {

    private final DailyQuizTargetUserPort targetUserPort = mock(DailyQuizTargetUserPort.class);
    private final DailyQuizUserGenerationService userGenerationService =
            mock(DailyQuizUserGenerationService.class);
    private final DailyQuizBatchExecutionService service =
            new DailyQuizBatchExecutionService(targetUserPort, userGenerationService);

    @Test
    void 사용자_데이터_오류는_격리하고_다음_사용자를_처리한다() {
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        when(targetUserPort.getTargetUsers(null, 100))
                .thenReturn(new DailyQuizTargetUserPage(List.of(firstUser, secondUser), null, false));
        when(userGenerationService.generate(firstUser, attemptDate))
                .thenThrow(new DailyQuizUserProcessingException(firstUser, attemptDate,
                        new BusinessException(ErrorCode.INVALID_INPUT_VALUE)));
        when(userGenerationService.generate(secondUser, attemptDate))
                .thenReturn(DailyQuizSetGenerationResult.alreadyExists());

        service.execute(attemptDate, 100);

        verify(userGenerationService).generate(firstUser, attemptDate);
        verify(userGenerationService).generate(secondUser, attemptDate);
    }

    @Test
    void 공통_장애는_사용자_오류로_간주하지_않고_전파한다() {
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        when(targetUserPort.getTargetUsers(null, 100))
                .thenReturn(new DailyQuizTargetUserPage(List.of(firstUser, secondUser), null, false));
        BusinessException failure = new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR);
        when(userGenerationService.generate(firstUser, attemptDate)).thenThrow(failure);

        assertThatThrownBy(() -> service.execute(attemptDate, 100)).isSameAs(failure);
        verify(userGenerationService, never()).generate(secondUser, attemptDate);
    }

    @Test
    void 대상_사용자_페이지_조회_실패를_정상_종료로_숨기지_않는다() {
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        BusinessException failure = new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR);
        when(targetUserPort.getTargetUsers(null, 100)).thenThrow(failure);

        assertThatThrownBy(() -> service.execute(attemptDate, 100)).isSameAs(failure);
        verifyNoInteractions(userGenerationService);
    }

    @Test
    void 대상_사용자_cursor가_순환하면_배치를_실패시킨다() {
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        UUID userId = UUID.randomUUID();
        DailyQuizTargetUserPage repeatedPage =
                new DailyQuizTargetUserPage(List.of(userId), userId, true);
        when(targetUserPort.getTargetUsers(null, 100)).thenReturn(repeatedPage);
        when(targetUserPort.getTargetUsers(userId, 100)).thenReturn(repeatedPage);
        when(userGenerationService.generate(userId, attemptDate))
                .thenReturn(DailyQuizSetGenerationResult.alreadyExists());

        assertThatThrownBy(() -> service.execute(attemptDate, 100))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEIGN_CLIENT_ERROR));
    }

    @Test
    void 실패한_날짜를_처음부터_재실행하면_기존_사용자는_건너뛰고_남은_사용자를_처리한다() {
        LocalDate attemptDate = LocalDate.of(2026, 9, 25);
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        when(targetUserPort.getTargetUsers(null, 100))
                .thenReturn(new DailyQuizTargetUserPage(List.of(firstUser, secondUser), null, false));
        when(userGenerationService.generate(firstUser, attemptDate))
                .thenReturn(DailyQuizSetGenerationResult.created(UUID.randomUUID(), 3))
                .thenReturn(DailyQuizSetGenerationResult.alreadyExists());
        BusinessException failure = new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR);
        when(userGenerationService.generate(secondUser, attemptDate))
                .thenThrow(failure)
                .thenReturn(DailyQuizSetGenerationResult.created(UUID.randomUUID(), 3));

        assertThatThrownBy(() -> service.execute(attemptDate, 100)).isSameAs(failure);
        service.execute(attemptDate, 100);

        verify(targetUserPort, times(2)).getTargetUsers(null, 100);
        verify(userGenerationService, times(2)).generate(firstUser, attemptDate);
        verify(userGenerationService, times(2)).generate(secondUser, attemptDate);
    }
}
