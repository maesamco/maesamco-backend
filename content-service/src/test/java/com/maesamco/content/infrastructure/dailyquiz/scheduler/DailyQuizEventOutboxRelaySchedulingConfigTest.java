package com.maesamco.content.infrastructure.dailyquiz.scheduler;

import com.maesamco.content.application.dailyquiz.scheduler.DailyQuizEventOutboxRelayScheduler;
import com.maesamco.content.application.dailyquiz.service.DailyQuizEventOutboxRelayService;
import com.maesamco.content.global.config.SchedulingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DailyQuizEventOutboxRelaySchedulingConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            SchedulingConfig.class,
                            DailyQuizEventOutboxRelaySchedulingConfig.class,
                            DailyQuizEventOutboxRelayScheduler.class
                    )
                    .withBean(
                            DailyQuizEventOutboxRelayService.class,
                            () -> mock(DailyQuizEventOutboxRelayService.class)
                    );

    @Test
    @DisplayName("Daily Quiz Outbox Relay가 비활성화되면 Scheduling 설정과 Scheduler를 생성하지 않는다")
    void schedulingIsDisabled_whenRelayEnabledIsFalse() {
        contextRunner
                .withPropertyValues(
                        "outbox.daily-quiz-completed.relay.enabled=false"
                )
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(
                                    DailyQuizEventOutboxRelaySchedulingConfig.class
                            );
                    assertThat(context)
                            .doesNotHaveBean(
                                    DailyQuizEventOutboxRelayScheduler.class
                            );
                });
    }

    @Test
    @DisplayName("Daily Quiz Outbox Relay가 활성화되면 Scheduling 설정과 Scheduler를 생성한다")
    void schedulingIsEnabled_whenRelayEnabledIsTrue() {
        contextRunner
                .withPropertyValues(
                        "outbox.daily-quiz-completed.relay.enabled=true",
                        "outbox.daily-quiz-completed.relay.fixed-delay-ms=60000"
                )
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(
                                    DailyQuizEventOutboxRelaySchedulingConfig.class
                            );
                    assertThat(context)
                            .hasSingleBean(
                                    DailyQuizEventOutboxRelayScheduler.class
                            );
                });
    }

    @Test
    @DisplayName("Problem과 Daily Quiz Relay를 함께 활성화해도 Scheduling 설정이 충돌하지 않는다")
    void schedulingStarts_whenBothRelaysAreEnabled() {
        contextRunner
                .withPropertyValues(
                        "outbox.problem-published.relay.enabled=true",
                        "outbox.daily-quiz-completed.relay.enabled=true",
                        "outbox.daily-quiz-completed.relay.fixed-delay-ms=60000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(SchedulingConfig.class);
                    assertThat(context)
                            .hasSingleBean(
                                    DailyQuizEventOutboxRelaySchedulingConfig.class
                            );
                    assertThat(context)
                            .hasSingleBean(
                                    DailyQuizEventOutboxRelayScheduler.class
                            );
                });
    }
}
