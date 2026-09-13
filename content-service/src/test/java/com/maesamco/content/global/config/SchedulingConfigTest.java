package com.maesamco.content.global.config;

import com.maesamco.content.problem.application.scheduler.ProblemEventOutboxRelayScheduler;
import com.maesamco.content.problem.application.service.ProblemEventOutboxRelayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            SchedulingConfig.class,
                            ProblemEventOutboxRelayScheduler.class
                    )
                    .withBean(
                            ProblemEventOutboxRelayService.class,
                            () -> mock(ProblemEventOutboxRelayService.class)
                    );

    @Test
    @DisplayName("Outbox Relay가 비활성화되면 스케줄링 설정과 Scheduler Bean을 생성하지 않는다")
    void schedulingIsDisabled_whenRelayEnabledIsFalse() {
        contextRunner
                .withPropertyValues(
                        "outbox.problem-published.relay.enabled=false"
                )
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(SchedulingConfig.class);

                    assertThat(context)
                            .doesNotHaveBean(
                                    ProblemEventOutboxRelayScheduler.class
                            );
                });
    }

    @Test
    @DisplayName("Outbox Relay가 활성화되면 스케줄링 설정과 Scheduler Bean을 생성한다")
    void schedulingIsEnabled_whenRelayEnabledIsTrue() {
        contextRunner
                .withPropertyValues(
                        "outbox.problem-published.relay.enabled=true",
                        "outbox.problem-published.relay.fixed-delay-ms=60000"
                )
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(SchedulingConfig.class);

                    assertThat(context)
                            .hasSingleBean(
                                    ProblemEventOutboxRelayScheduler.class
                            );
                });
    }
}
