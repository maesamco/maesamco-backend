package com.maesamco.content.infrastructure.dailyquiz.scheduler;

import com.maesamco.content.application.dailyquiz.service.DailyQuizBatchExecutionService;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DailyQuizBatchSchedulerTest {

    @Test
    void 설정한_시간대의_퀴즈_날짜와_청크_크기로_배치를_실행한다() {
        DailyQuizBatchExecutionService executionService = mock(DailyQuizBatchExecutionService.class);
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);
        DailyQuizBatchProperties properties = new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100, 2, 300_000
        );
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        DailyQuizBatchScheduler scheduler = new DailyQuizBatchScheduler(
                executionService, properties, clock, retryExecutor
        );

        scheduler.run();

        verify(executionService).execute(LocalDate.of(2026, 9, 23), 100);
        verifyNoInteractions(retryExecutor);
    }

    @Test
    void 실패하면_같은_날짜로_재실행하고_성공하면_추가_예약하지_않는다() {
        DailyQuizBatchExecutionService executionService = mock(DailyQuizBatchExecutionService.class);
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);
        DailyQuizBatchProperties properties = new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100, 2, 300_000
        );
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        DailyQuizBatchScheduler scheduler = new DailyQuizBatchScheduler(
                executionService, properties, clock, retryExecutor
        );
        LocalDate attemptDate = LocalDate.of(2026, 9, 23);
        doThrow(new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR))
                .doNothing()
                .when(executionService).execute(attemptDate, 100);

        scheduler.run();

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(retryExecutor).schedule(retry.capture(), eq(300_000L), eq(TimeUnit.MILLISECONDS));
        retry.getValue().run();

        verify(executionService, times(2)).execute(attemptDate, 100);
        verifyNoMoreInteractions(retryExecutor);
    }

    @Test
    void 연속_실패하면_설정된_횟수까지만_재실행한다() {
        DailyQuizBatchExecutionService executionService = mock(DailyQuizBatchExecutionService.class);
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);
        DailyQuizBatchProperties properties = new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100, 2, 300_000
        );
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        DailyQuizBatchScheduler scheduler = new DailyQuizBatchScheduler(
                executionService, properties, clock, retryExecutor
        );
        LocalDate attemptDate = LocalDate.of(2026, 9, 23);
        doThrow(new BusinessException(ErrorCode.FEIGN_CLIENT_ERROR))
                .when(executionService).execute(attemptDate, 100);

        scheduler.run();
        ArgumentCaptor<Runnable> retries = ArgumentCaptor.forClass(Runnable.class);
        verify(retryExecutor).schedule(retries.capture(), eq(300_000L), eq(TimeUnit.MILLISECONDS));
        retries.getValue().run();
        verify(retryExecutor, times(2))
                .schedule(retries.capture(), eq(300_000L), eq(TimeUnit.MILLISECONDS));
        retries.getAllValues().getLast().run();

        verify(executionService, times(3)).execute(attemptDate, 100);
        verifyNoMoreInteractions(retryExecutor);
    }

    @Test
    void 코드_오류는_자동_재시도하지_않는다() {
        DailyQuizBatchExecutionService executionService = mock(DailyQuizBatchExecutionService.class);
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);
        DailyQuizBatchProperties properties = new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100, 2, 300_000
        );
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        DailyQuizBatchScheduler scheduler = new DailyQuizBatchScheduler(
                executionService, properties, clock, retryExecutor
        );
        doThrow(new IllegalStateException("programming error"))
                .when(executionService).execute(LocalDate.of(2026, 9, 23), 100);

        scheduler.run();

        verifyNoInteractions(retryExecutor);
    }

    @Test
    void 실행이_겹치면_재시도_횟수를_소모하지_않고_같은_날짜로_연기한다() {
        DailyQuizBatchExecutionService executionService = mock(DailyQuizBatchExecutionService.class);
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);
        DailyQuizBatchProperties properties = new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100, 2, 300_000
        );
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        DailyQuizBatchScheduler scheduler = new DailyQuizBatchScheduler(
                executionService, properties, clock, retryExecutor
        );
        LocalDate attemptDate = LocalDate.of(2026, 9, 23);
        doAnswer(invocation -> {
            scheduler.run();
            return null;
        }).doNothing().when(executionService).execute(attemptDate, 100);

        scheduler.run();

        ArgumentCaptor<Runnable> deferred = ArgumentCaptor.forClass(Runnable.class);
        verify(retryExecutor).schedule(deferred.capture(), eq(300_000L), eq(TimeUnit.MILLISECONDS));
        deferred.getValue().run();
        verify(executionService, times(2)).execute(attemptDate, 100);
        verifyNoMoreInteractions(retryExecutor);
    }

    @Test
    void DB_접근_오류도_재시도한다() {
        DailyQuizBatchExecutionService executionService = mock(DailyQuizBatchExecutionService.class);
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);
        DailyQuizBatchProperties properties = new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100, 2, 300_000
        );
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        DailyQuizBatchScheduler scheduler = new DailyQuizBatchScheduler(
                executionService, properties, clock, retryExecutor
        );
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(executionService).execute(LocalDate.of(2026, 9, 23), 100);

        scheduler.run();

        verify(retryExecutor).schedule(any(Runnable.class), eq(300_000L), eq(TimeUnit.MILLISECONDS));
    }
}
