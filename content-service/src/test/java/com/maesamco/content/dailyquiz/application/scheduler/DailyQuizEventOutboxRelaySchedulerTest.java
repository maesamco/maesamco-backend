package com.maesamco.content.dailyquiz.application.scheduler;

import com.maesamco.content.application.dailyquiz.scheduler.DailyQuizEventOutboxRelayScheduler;
import com.maesamco.content.application.dailyquiz.service.DailyQuizEventOutboxRelayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DailyQuizEventOutboxRelaySchedulerTest {

    @Test
    @DisplayName("스케줄 실행 시 Daily Quiz PENDING Outbox Relay를 호출한다")
    void relayPendingOutboxes_callsRelayService() {
        DailyQuizEventOutboxRelayService relayService =
                mock(DailyQuizEventOutboxRelayService.class);

        DailyQuizEventOutboxRelayScheduler scheduler =
                new DailyQuizEventOutboxRelayScheduler(
                        relayService
                );

        scheduler.relayPendingOutboxes();

        verify(relayService).relayPendingOutboxes();
    }
}
