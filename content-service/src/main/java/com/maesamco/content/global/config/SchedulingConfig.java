package com.maesamco.content.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 애플리케이션 스케줄링 설정입니다.
 *
 * ProblemPublished Outbox Relay가 활성화된 환경에서 Spring Scheduling 인프라를 활성화하고,
 * ProblemPublished Relay와 DailyQuizCompleted Relay가 서로의 실행을 지연시키지 않도록
 * 공용 스케줄러 스레드 풀을 구성합니다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "outbox.problem-published.relay",
        name = "enabled",
        havingValue = "true"
)
public class SchedulingConfig implements SchedulingConfigurer {

    private static final int SCHEDULER_POOL_SIZE = 2;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("Scheduled-task-");
        return scheduler;
    }
}
