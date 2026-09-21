package com.maesamco.content.global.config;

import com.maesamco.content.application.facade.ProblemEventRelayFacade;
import com.maesamco.content.application.port.EventPublisherPort;
import com.maesamco.content.application.persistence_service.ProblemEventOutboxPersistenceService;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            SchedulingConfig.class,
                            ProblemEventRelayFacade.class
                    )
                    .withBean(
                            ProblemEventOutboxRepository.class,
                            () -> mock(ProblemEventOutboxRepository.class)
                    )
                    .withBean(
                            ProblemEventOutboxPersistenceService.class,
                            () -> mock(ProblemEventOutboxPersistenceService.class)
                    )
                    .withBean(
                            EventPublisherPort.class,
                            () -> mock(EventPublisherPort.class)
                    );

    @Test
    @DisplayName("Outbox Relay가 비활성화되면 Relay Facade Bean을 생성하지 않는다")
    void relayIsDisabled_whenRelayEnabledIsFalse() {
        contextRunner
                .withPropertyValues(
                        "outbox.problem-published.relay.enabled=false"
                )
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(SchedulingConfig.class);

                    assertThat(context)
                            .doesNotHaveBean(
                                    ProblemEventRelayFacade.class
                            );
                });
    }

    @Test
    @DisplayName("Outbox Relay가 활성화되면 Relay Facade Bean과 전용 TaskScheduler를 생성한다")
    void relayIsEnabled_whenRelayEnabledIsTrue() {
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
                                    ProblemEventRelayFacade.class
                            );

                    ThreadPoolTaskScheduler taskScheduler =
                            context.getBean(
                                    ThreadPoolTaskScheduler.class
                            );

                    assertThat(
                            taskScheduler
                                    .getScheduledThreadPoolExecutor()
                                    .getCorePoolSize()
                    ).isEqualTo(2);
                });
    }
}