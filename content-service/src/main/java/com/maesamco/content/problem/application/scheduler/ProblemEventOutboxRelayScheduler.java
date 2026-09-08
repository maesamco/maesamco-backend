package com.maesamco.content.problem.application.scheduler;

import com.maesamco.content.problem.application.service.ProblemEventOutboxRelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "outbox.problem-published.relay",
        name = "enabled",
        havingValue = "true"
)
public class ProblemEventOutboxRelayScheduler {

    private final ProblemEventOutboxRelayService relayService;

    @Scheduled(
            fixedDelayString =
                    "${outbox.problem-published.relay.fixed-delay-ms:1000}"
    )
    public void relayPendingOutboxes() {
        relayService.relayPendingOutboxes();
    }
}
