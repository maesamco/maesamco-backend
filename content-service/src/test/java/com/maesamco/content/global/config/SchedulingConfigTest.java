package com.maesamco.content.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SchedulingConfigTest {

    @Test
    @DisplayName("Scheduled 작업용 ThreadPoolTaskScheduler를 생성한다")
    void outboxRelayTaskScheduler_createsScheduler() {
        // given
        SchedulingConfig schedulingConfig = new SchedulingConfig();

        // when
        ThreadPoolTaskScheduler scheduler =
                schedulingConfig.outboxRelayTaskScheduler();

        // then
        assertThat(scheduler).isNotNull();
    }

    @Test
    @DisplayName("ScheduledTaskRegistrar에 전용 TaskScheduler를 설정한다")
    void configureTasks_setsTaskScheduler() {
        // given
        SchedulingConfig schedulingConfig = new SchedulingConfig();
        ScheduledTaskRegistrar registrar =
                mock(ScheduledTaskRegistrar.class);

        // when
        schedulingConfig.configureTasks(registrar);

        // then
        verify(registrar)
                .setTaskScheduler(
                        any(ThreadPoolTaskScheduler.class)
                );
    }
}