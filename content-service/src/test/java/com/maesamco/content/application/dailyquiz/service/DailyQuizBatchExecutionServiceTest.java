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
}
