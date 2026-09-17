package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Daily Quiz Event Outbox의 PostgreSQL 저장과 Relay 조회 조건을 검증합니다.
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
        DailyQuizEventOutboxRepositoryImpl.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@EnableJpaRepositories(
        basePackageClasses = SpringDataDailyQuizEventOutboxRepository.class
)
class DailyQuizEventOutboxRepositoryTest {

    private static final JsonMapper JSON_MAPPER =
            JsonMapper.builder().build();

    private static final Instant BASE_TIME =
            Instant.parse("2026-09-17T00:00:00Z");

    private static final Instant AVAILABLE_AT =
            Instant.parse("2026-09-17T01:00:00Z");

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    static {
        postgres.start();
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DailyQuizEventOutboxRepository outboxRepository;

    @Test
    @DisplayName("Daily Quiz Outbox를 ID로 다시 조회할 수 있다")
    void findById_returnsSavedOutbox() throws Exception {
        DailyQuizEventOutbox saved =
                outboxRepository.save(
                        createPendingOutbox(BASE_TIME)
                );

        UUID savedId = saved.getId();

        entityManager.flush();
        entityManager.clear();

        DailyQuizEventOutbox found =
                outboxRepository.findById(savedId)
                        .orElseThrow();

        assertThat(found.getId()).isEqualTo(savedId);
        assertThat(
                JSON_MAPPER.readTree(found.getPayload())
        ).isEqualTo(
                JSON_MAPPER.readTree(
                        "{\"eventType\":\"DAILY_QUIZ_COMPLETED\"}"
                )
        );
    }

    @Test
    @DisplayName("발행 가능한 PENDING Outbox만 오래된 순서대로 조회한다")
    void findPublishablePending_filtersByStatusAndNextAttemptAt() {
        DailyQuizEventOutbox immediate =
                createPendingOutbox(BASE_TIME);

        DailyQuizEventOutbox retryReady =
                createPendingOutbox(BASE_TIME.plusSeconds(1));
        retryReady.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                AVAILABLE_AT.minusSeconds(1)
        );

        DailyQuizEventOutbox retryReadyAtBoundary =
                createPendingOutbox(BASE_TIME.plusSeconds(2));
        retryReadyAtBoundary.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                AVAILABLE_AT
        );

        DailyQuizEventOutbox backingOff =
                createPendingOutbox(BASE_TIME.minusSeconds(3));
        backingOff.recordPublishFailure(
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                AVAILABLE_AT.plusSeconds(1)
        );

        DailyQuizEventOutbox published =
                createPendingOutbox(BASE_TIME.minusSeconds(2));
        published.recordPublishSuccess(AVAILABLE_AT);

        DailyQuizEventOutbox failed =
                createPendingOutbox(BASE_TIME.minusSeconds(1));
        failed.recordUnrecoverablePublishFailure(
                "EVENT_PAYLOAD_TOO_LARGE"
        );

        saveAll(
                backingOff,
                published,
                failed,
                retryReadyAtBoundary,
                retryReady,
                immediate
        );

        UUID immediateId = immediate.getId();
        UUID retryReadyId = retryReady.getId();
        UUID retryReadyAtBoundaryId = retryReadyAtBoundary.getId();

        entityManager.flush();
        entityManager.clear();

        List<DailyQuizEventOutbox> found =
                outboxRepository.findPublishablePending(
                        AVAILABLE_AT,
                        10
                );

        assertThat(found)
                .extracting(DailyQuizEventOutbox::getId)
                .containsExactly(
                        immediateId,
                        retryReadyId,
                        retryReadyAtBoundaryId
                );
    }

    @Test
    @DisplayName("발행 가능한 Outbox를 배치 크기만큼만 조회한다")
    void findPublishablePending_limitsBatchSize() {
        DailyQuizEventOutbox oldest =
                createPendingOutbox(BASE_TIME);
        DailyQuizEventOutbox second =
                createPendingOutbox(BASE_TIME.plusSeconds(1));
        DailyQuizEventOutbox third =
                createPendingOutbox(BASE_TIME.plusSeconds(2));

        saveAll(third, second, oldest);

        UUID oldestId = oldest.getId();
        UUID secondId = second.getId();

        entityManager.flush();
        entityManager.clear();

        List<DailyQuizEventOutbox> found =
                outboxRepository.findPublishablePending(
                        AVAILABLE_AT,
                        2
                );

        assertThat(found)
                .extracting(DailyQuizEventOutbox::getId)
                .containsExactly(oldestId, secondId);
    }

    private void saveAll(DailyQuizEventOutbox... outboxes) {
        for (DailyQuizEventOutbox outbox : outboxes) {
            outboxRepository.save(outbox);
        }
    }

    private DailyQuizEventOutbox createPendingOutbox(
            Instant occurredAt
    ) {
        return DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DAILY_QUIZ_COMPLETED",
                1,
                "{\"eventType\":\"DAILY_QUIZ_COMPLETED\"}",
                occurredAt
        );
    }
}
