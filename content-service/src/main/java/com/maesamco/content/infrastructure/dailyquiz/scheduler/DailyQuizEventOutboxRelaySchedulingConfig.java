package com.maesamco.content.infrastructure.dailyquiz.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Daily Quiz Outbox Relay가 활성화된 환경에서만
 * Spring Scheduling 인프라를 활성화
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "outbox.daily-quiz-completed.relay",
        name = "enabled",
        havingValue = "true"
)
public class DailyQuizEventOutboxRelaySchedulingConfig {
}
