package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    private static final Instant LEASE_UNTIL =
            Instant.parse("2026-09-17T01:05:00Z");

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
    private EntityManager entityManager;

    @Autowired
    private DailyQuizEventOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        claimForSetup(retryReady);
        retryReady.recordPublishFailure(
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                AVAILABLE_AT.minusSeconds(1)
        );

        DailyQuizEventOutbox retryReadyAtBoundary =
                createPendingOutbox(BASE_TIME.plusSeconds(2));
        claimForSetup(retryReadyAtBoundary);
        retryReadyAtBoundary.recordPublishFailure(
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                AVAILABLE_AT
        );

        DailyQuizEventOutbox backingOff =
                createPendingOutbox(BASE_TIME.minusSeconds(3));
        claimForSetup(backingOff);
        backingOff.recordPublishFailure(
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                AVAILABLE_AT.plusSeconds(1)
        );

        DailyQuizEventOutbox published =
                createPendingOutbox(BASE_TIME.minusSeconds(2));
        claimForSetup(published);
        published.recordPublishSuccess(CLAIM_ID, AVAILABLE_AT);

        DailyQuizEventOutbox failed =
                createPendingOutbox(BASE_TIME.minusSeconds(1));
        claimForSetup(failed);
        failed.recordUnrecoverablePublishFailure(
                CLAIM_ID,
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
                outboxRepository.claimPublishable(
                        AVAILABLE_AT,
                        LEASE_UNTIL,
                        CLAIM_ID,
                        10
                );

        assertThat(found)
                .extracting(DailyQuizEventOutbox::getId)
                .containsExactly(
                        immediateId,
                        retryReadyId,
                        retryReadyAtBoundaryId
                );
        assertThat(found)
                .allSatisfy(outbox -> {
                    assertThat(outbox.getStatus())
                            .isEqualTo(DailyQuizEventOutboxStatus.IN_PROGRESS);
                    assertThat(outbox.getClaimId()).isEqualTo(CLAIM_ID);
                    assertThat(outbox.getLeaseUntil()).isEqualTo(LEASE_UNTIL);
                });
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
                outboxRepository.claimPublishable(
                        AVAILABLE_AT,
                        LEASE_UNTIL,
                        CLAIM_ID,
                        2
                );

        assertThat(found)
                .extracting(DailyQuizEventOutbox::getId)
                .containsExactly(oldestId, secondId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("두 Relay Worker가 동시에 선점해도 동일 Outbox는 한 Worker만 가져간다")
    void claimPublishable_allowsOnlyOneWorkerToClaimSameOutbox() throws Exception {
        jdbcTemplate.update(
                "DELETE FROM content_schema.p_daily_quiz_event_outboxes"
        );
        DailyQuizEventOutbox saved =
                outboxRepository.save(createPendingOutbox(BASE_TIME));
        UUID firstClaimId = UUID.randomUUID();
        UUID secondClaimId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<DailyQuizEventOutbox>> first = executor.submit(() -> {
                start.await();
                return outboxRepository.claimPublishable(
                        AVAILABLE_AT,
                        LEASE_UNTIL,
                        firstClaimId,
                        1
                );
            });
            Future<List<DailyQuizEventOutbox>> second = executor.submit(() -> {
                start.await();
                return outboxRepository.claimPublishable(
                        AVAILABLE_AT,
                        LEASE_UNTIL,
                        secondClaimId,
                        1
                );
            });

            start.countDown();

            List<DailyQuizEventOutbox> firstResult = first.get();
            List<DailyQuizEventOutbox> secondResult = second.get();

            assertThat(firstResult.size() + secondResult.size()).isEqualTo(1);
            assertThat(firstResult.isEmpty() ? secondResult : firstResult)
                    .singleElement()
                    .extracting(DailyQuizEventOutbox::getId)
                    .isEqualTo(saved.getId());

            DailyQuizEventOutbox found =
                    outboxRepository.findById(saved.getId()).orElseThrow();
            assertThat(found.getStatus())
                    .isEqualTo(DailyQuizEventOutboxStatus.IN_PROGRESS);
            assertThat(found.getClaimId())
                    .isIn(firstClaimId, secondClaimId);
        } finally {
            executor.shutdownNow();
            jdbcTemplate.update(
                    "DELETE FROM content_schema.p_daily_quiz_event_outboxes"
            );
        }
    }

    @Test
    @DisplayName("lease가 만료된 IN_PROGRESS Outbox는 새로운 Worker가 재선점한다")
    void claimPublishable_reclaimsExpiredLease() {
        DailyQuizEventOutbox saved =
                outboxRepository.save(createPendingOutbox(BASE_TIME));
        UUID firstClaimId = UUID.randomUUID();
        UUID secondClaimId = UUID.randomUUID();

        outboxRepository.claimPublishable(
                AVAILABLE_AT,
                LEASE_UNTIL,
                firstClaimId,
                1
        );
        entityManager.clear();

        List<DailyQuizEventOutbox> beforeExpiration =
                outboxRepository.claimPublishable(
                        LEASE_UNTIL.minusMillis(1),
                        LEASE_UNTIL.plusSeconds(60),
                        secondClaimId,
                        1
                );
        List<DailyQuizEventOutbox> afterExpiration =
                outboxRepository.claimPublishable(
                        LEASE_UNTIL,
                        LEASE_UNTIL.plusSeconds(60),
                        secondClaimId,
                        1
                );

        assertThat(beforeExpiration).isEmpty();
        assertThat(afterExpiration)
                .singleElement()
                .satisfies(outbox -> {
                    assertThat(outbox.getId()).isEqualTo(saved.getId());
                    assertThat(outbox.getClaimId()).isEqualTo(secondClaimId);
                });
    }

    private void claimForSetup(DailyQuizEventOutbox outbox) {
        outbox.claimForPublish(
                CLAIM_ID,
                BASE_TIME.minusSeconds(1),
                BASE_TIME.plusSeconds(30)
        );
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
