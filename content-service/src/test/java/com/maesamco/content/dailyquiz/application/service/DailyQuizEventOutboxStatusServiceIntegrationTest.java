package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.application.dailyquiz.service.DailyQuizEventOutboxStatusService;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.infrastructure.dailyquiz.persistence.DailyQuizEventOutboxRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Daily Quiz Outbox 상태 변경의 트랜잭션과 낙관적 락을
 * 실제 PostgreSQL에서 검증합니다.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        DailyQuizEventOutboxRepositoryImpl.class,
        DailyQuizEventOutboxStatusService.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@EnableJpaRepositories(
        basePackageClasses = DailyQuizEventOutboxRepositoryImpl.class
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DailyQuizEventOutboxStatusServiceIntegrationTest {

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-17T00:00:00Z");

    private static final Instant NEXT_ATTEMPT_AT =
            Instant.parse("2026-09-17T00:01:00Z");

    private static final Instant PUBLISHED_AT =
            Instant.parse("2026-09-17T00:02:00Z");

    private static final Instant LEASE_UNTIL =
            Instant.parse("2026-09-17T00:05:00Z");

    private static final UUID CLAIM_ID = UUID.randomUUID();

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    static {
        postgres.start();
    }

    @Autowired
    private DailyQuizEventOutboxRepository outboxRepository;

    @Autowired
    private DailyQuizEventOutboxStatusService statusService;

    @Test
    @DisplayName("발행 성공 결과를 별도 트랜잭션으로 저장한다")
    void recordPublishSuccess_persistsStateAndVersion() {
        DailyQuizEventOutbox saved = savePendingOutbox();

        statusService.recordPublishSuccess(
                saved.getId(),
                CLAIM_ID,
                PUBLISHED_AT
        );

        DailyQuizEventOutbox found = find(saved.getId());

        assertThat(found.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PUBLISHED);
        assertThat(found.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(found.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("발행 실패 결과와 다음 시도 시각을 별도 트랜잭션으로 저장한다")
    void recordPublishFailure_persistsRetryState() {
        DailyQuizEventOutbox saved = savePendingOutbox();

        statusService.recordPublishFailure(
                saved.getId(),
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                NEXT_ATTEMPT_AT
        );

        DailyQuizEventOutbox found = find(saved.getId());

        assertThat(found.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PENDING);
        assertThat(found.getRetryCount()).isEqualTo(1);
        assertThat(found.getNextAttemptAt()).isEqualTo(NEXT_ATTEMPT_AT);
        assertThat(found.getLastError()).isEqualTo("KAFKA_PUBLISH_TIMEOUT");
        assertThat(found.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("불확실한 발행 결과를 PENDING 상태와 함께 별도 트랜잭션으로 저장한다")
    void recordPublishOutcomeUnknown_persistsRetryStateWithoutFailure() {
        DailyQuizEventOutbox saved = savePendingOutbox();

        statusService.recordPublishOutcomeUnknown(
                saved.getId(),
                CLAIM_ID,
                "KAFKA_PUBLISH_OUTCOME_UNKNOWN",
                NEXT_ATTEMPT_AT
        );

        DailyQuizEventOutbox found = find(saved.getId());

        assertThat(found.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PENDING);
        assertThat(found.getRetryCount()).isEqualTo(1);
        assertThat(found.getNextAttemptAt()).isEqualTo(NEXT_ATTEMPT_AT);
        assertThat(found.getLastError())
                .isEqualTo("KAFKA_PUBLISH_OUTCOME_UNKNOWN");
        assertThat(found.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 종료된 Outbox를 다시 변경하면 DB 상태와 version을 유지한다")
    void terminalOutboxUpdate_isIgnoredWithoutVersionChange() {
        DailyQuizEventOutbox saved = savePendingOutbox();
        UUID outboxId = saved.getId();

        statusService.recordPublishSuccess(
                outboxId,
                CLAIM_ID,
                PUBLISHED_AT
        );

        statusService.recordPublishFailure(
                outboxId,
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                NEXT_ATTEMPT_AT
        );

        DailyQuizEventOutbox found = find(outboxId);

        assertThat(found.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PUBLISHED);
        assertThat(found.getRetryCount()).isZero();
        assertThat(found.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(found.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 version으로 두 번 상태를 변경하면 낙관적 락 충돌이 발생한다")
    void staleOutboxUpdate_throwsOptimisticLockingFailure() {
        DailyQuizEventOutbox saved = savePendingOutbox();
        UUID outboxId = saved.getId();

        DailyQuizEventOutbox first = find(outboxId);
        DailyQuizEventOutbox stale = find(outboxId);

        first.recordPublishSuccess(CLAIM_ID, PUBLISHED_AT);
        outboxRepository.save(first);

        stale.recordPublishFailure(
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                NEXT_ATTEMPT_AT
        );

        assertThatThrownBy(() -> outboxRepository.save(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        DailyQuizEventOutbox found = find(outboxId);

        assertThat(found.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PUBLISHED);
        assertThat(found.getVersion()).isEqualTo(1L);
    }

    private DailyQuizEventOutbox savePendingOutbox() {
        DailyQuizEventOutbox outbox =
                DailyQuizEventOutbox.createPending(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "DAILY_QUIZ_COMPLETED",
                        1,
                        "{\"eventType\":\"DAILY_QUIZ_COMPLETED\"}",
                        OCCURRED_AT
                );
        outbox.claimForPublish(
                CLAIM_ID,
                NEXT_ATTEMPT_AT,
                LEASE_UNTIL
        );
        return outboxRepository.save(outbox);
    }

    private DailyQuizEventOutbox find(UUID outboxId) {
        return outboxRepository.findById(outboxId)
                .orElseThrow();
    }
}
