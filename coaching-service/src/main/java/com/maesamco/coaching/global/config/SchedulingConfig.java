package com.maesamco.coaching.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Judge Service(이슈 #63)와 동일한 설계 — Spring Boot 기본 스케줄러(풀 크기 1)를 그대로
 * 쓰면 Outbox Relay 한 건이 오래 걸릴 때 다른 스케줄 작업까지 밀린다.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private static final int SCHEDULER_POOL_SIZE = 2;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        // initialize()를 직접 호출하지 않는다 — ThreadPoolTaskScheduler는 InitializingBean이라
        // 컨테이너가 빈 생명주기에서 afterPropertiesSet()으로 알아서 초기화한다
        // (PR #120 리뷰 반영, 용현님 P4).
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("Scheduled-task-");
        return scheduler;
    }
}
