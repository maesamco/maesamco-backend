package com.maesamco.content.application.dailyquiz.scheduler;

import com.maesamco.content.application.dailyquiz.service.DailyQuizEventOutboxRelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 설정된 주기마다 발행 가능한 DailyQuizCompleted Outbox Relay를 실행
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "outbox.daily-quiz-completed.relay",
        name = "enabled",
        havingValue = "true"
)
public class DailyQuizEventOutboxRelayScheduler {

    private final DailyQuizEventOutboxRelayService relayService;

    @Scheduled(fixedDelayString = "${outbox.daily-quiz-completed.relay.fixed-delay-ms:1000}")
    public void relayPendingOutboxes() {
        relayService.relayPendingOutboxes();
    }
}
