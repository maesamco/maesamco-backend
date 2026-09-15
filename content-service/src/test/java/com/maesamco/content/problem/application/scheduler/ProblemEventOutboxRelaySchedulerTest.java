package com.maesamco.content.problem.application.scheduler;

import com.maesamco.content.application.scheduler.ProblemEventOutboxRelayScheduler;
import com.maesamco.content.application.service.event_outbox.ProblemEventOutboxRelayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProblemEventOutboxRelaySchedulerTest {

    @Test
    @DisplayName("스케줄 실행 시 PENDING Outbox Relay를 호출한다")
    void relayPendingOutboxes_callsRelayService() {
        // given
        ProblemEventOutboxRelayService relayService =
                mock(ProblemEventOutboxRelayService.class);

        ProblemEventOutboxRelayScheduler scheduler =
                new ProblemEventOutboxRelayScheduler(
                        relayService
                );

        // when
        scheduler.relayPendingOutboxes();

        // then
        verify(relayService).relayPendingOutboxes();
    }
}
