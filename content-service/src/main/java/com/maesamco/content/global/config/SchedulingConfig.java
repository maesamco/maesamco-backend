package com.maesamco.content.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Content Service의 Scheduled 작업이 사용할 스레드 풀을 설정합니다.
 * Outbox Relay와 다른 Scheduled 작업이 서로의 실행을 지연시키지 않도록 합니다.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    // 여러 Scheduled 작업이 서로의 실행을 지연시키지 않도록 최소 병렬 실행을 허용합니다.
    private static final int SCHEDULER_POOL_SIZE = 2;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(outboxRelayTaskScheduler());
    }

    @Bean
    public ThreadPoolTaskScheduler outboxRelayTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("Scheduled-task-");
        return scheduler;
    }
}