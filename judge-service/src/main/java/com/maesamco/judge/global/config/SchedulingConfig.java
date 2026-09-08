package com.maesamco.judge.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private static final int SCHEDULER_POOL_SIZE = 2;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskS());
    }

    @Bean
    public ThreadPoolTaskScheduler outboxRelayTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("Scheduled-task-");
        return scheduler;
    }
}
