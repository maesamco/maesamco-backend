package com.maesamco.content.infrastructure.dailyquiz.scheduler;

import com.maesamco.content.application.dailyquiz.service.DailyQuizBatchExecutionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DailyQuizBatchSchedulerTest {

    @Test
    void 설정한_시간대의_퀴즈_날짜와_청크_크기로_배치를_실행한다() {
        DailyQuizBatchExecutionService executionService = mock(DailyQuizBatchExecutionService.class);
        DailyQuizBatchProperties properties = new DailyQuizBatchProperties(
                "0 0 3 * * *", "Asia/Seoul", 100
        );
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        DailyQuizBatchScheduler scheduler = new DailyQuizBatchScheduler(executionService, properties, clock);

        scheduler.run();

        verify(executionService).execute(LocalDate.of(2026, 9, 23), 100);
    }
}
