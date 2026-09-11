package com.maesamco.content.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 스케줄링 설정입니다.
 *
 * <p>ProblemPublished Outbox Relay가 활성화된 환경에서만
 * Spring Scheduling 인프라를 활성화합니다.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "outbox.problem-published.relay",
        name = "enabled",
        havingValue = "true"
)
public class SchedulingConfig {
}
