package com.maesamco.content.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maesamco.content.application.facade.ProblemEventRelayFacade;
import com.maesamco.content.application.persistence_service.ProblemEventOutboxPersistenceService;
import com.maesamco.content.application.port.EventPublisherPort;
import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.maesamco.content.infrastructure.persistence.ProblemEventOutboxRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        ProblemEventOutboxRepositoryImpl.class,
        ProblemEventOutboxPersistenceService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProblemEventOutboxRelayIntegrationTest {

    private static final String TOPIC = "problem-published";
    private static final int BATCH_SIZE = 10;
    private static final int MAX_PAYLOAD_BYTES = 10_000;

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }

    @Autowired
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    @Autowired
    private ProblemEventOutboxPersistenceService problemEventOutboxPersistenceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private EventPublisherPort eventPublisherPort;
    private ProblemEventRelayFacade problemEventRelayFacade;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM content_schema.p_problem_event_outboxes");

        eventPublisherPort = mock(EventPublisherPort.class);

        problemEventRelayFacade = new ProblemEventRelayFacade(
                problemEventOutboxRepository,
                problemEventOutboxPersistenceService,
                eventPublisherPort
        );

        ReflectionTestUtils.setField(problemEventRelayFacade, "problemPublishedTopic", TOPIC);
        ReflectionTestUtils.setField(problemEventRelayFacade, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(problemEventRelayFacade, "maxPayloadBytes", MAX_PAYLOAD_BYTES);
    }

    @Test
    @DisplayName("재시도 시각이 지난 PENDING Outbox를 relay하면 Kafka 발행 후 PUBLISHED 상태로 변경한다")
    void relay_dueOutbox_marksPublished() throws Exception {
        // given
        ProblemEventOutbox outbox = savePendingOutbox();
        UUID outboxId = outbox.getId();

        setNextAttemptAtToPast(outboxId);

        OutboxState before = readOutbox(outboxId);

        // when
        problemEventRelayFacade.relay();

        // then
        OutboxState after = readOutbox(outboxId);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(eventPublisherPort).publish(
                eq(TOPIC),
                eq(outbox.getAggregateId().toString()),
                payloadCaptor.capture()
        );

        ObjectMapper objectMapper = new ObjectMapper();

        assertThat(objectMapper.readTree(payloadCaptor.getValue()))
                .isEqualTo(objectMapper.readTree(outbox.getPayload()));

        assertThat(before.status()).isEqualTo(ProblemEventOutboxStatus.PENDING);
        assertThat(after.status()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
        assertThat(after.publishedAt()).isNotNull();
        assertThat(after.retryCount()).isZero();
        assertThat(after.lockVersion()).isEqualTo(before.lockVersion() + 1);
    }

    @Test
    @DisplayName("nextAttemptAt이 미래인 PENDING Outbox는 relay 대상에서 제외한다")
    void relay_futureNextAttemptAt_doesNotPublish() {
        // given
        ProblemEventOutbox outbox = savePendingOutbox();
        UUID outboxId = outbox.getId();

        setNextAttemptAtToFuture(outboxId);

        // when
        problemEventRelayFacade.relay();

        // then
        OutboxState state = readOutbox(outboxId);

        verifyNoInteractions(eventPublisherPort);

        assertThat(state.status()).isEqualTo(ProblemEventOutboxStatus.PENDING);
        assertThat(state.retryCount()).isZero();
        assertThat(state.nextAttemptAt()).isNotNull();
        assertThat(state.publishedAt()).isNull();
        assertThat(state.lockVersion()).isZero();
    }

    @Test
    @DisplayName("Kafka 발행이 실패하면 PENDING 상태를 유지하고 retryCount와 nextAttemptAt을 갱신한다")
    void relay_publishFailure_updatesRetryBackoff() {
        // given
        ProblemEventOutbox outbox = savePendingOutbox();
        UUID outboxId = outbox.getId();

        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(eventPublisherPort)
                .publish(eq(TOPIC), eq(outbox.getAggregateId().toString()), anyString());

        Instant before = Instant.now();

        // when
        problemEventRelayFacade.relay();

        // then
        OutboxState state = readOutbox(outboxId);

        verify(eventPublisherPort).publish(
                eq(TOPIC),
                eq(outbox.getAggregateId().toString()),
                anyString()
        );

        assertThat(state.status()).isEqualTo(ProblemEventOutboxStatus.PENDING);
        assertThat(state.retryCount()).isEqualTo(1);
        assertThat(state.nextAttemptAt()).isAfter(before);
        assertThat(state.publishedAt()).isNull();
        assertThat(state.lastError()).isEqualTo("KAFKA_PUBLISH_FAILED:IllegalStateException");
        assertThat(state.lockVersion()).isEqualTo(1L);
    }

    // TODO: 다중 인스턴스에서 같은 Outbox를 동시에 claim해서 중복 발행되는 문제에 대한 처리 구현 및 테스토 코드 작성
//    @Test
//    @DisplayName("두 worker가 동시에 같은 Outbox를 relay해도 Kafka 발행은 한 번만 수행한다")
//    void relay_concurrently_sameOutbox_publishesOnlyOnce() throws Exception {
//        // given
//        ProblemEventOutbox outbox = savePendingOutbox();
//        UUID outboxId = outbox.getId();
//
//        AtomicInteger publishCount = new AtomicInteger();
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch firstPublishEntered = new CountDownLatch(1);
//        CountDownLatch releaseFirstPublish = new CountDownLatch(1);
//
//        doAnswer(invocation -> {
//            int currentCount = publishCount.incrementAndGet();
//
//            if (currentCount == 1) {
//                firstPublishEntered.countDown();
//                releaseFirstPublish.await(2, TimeUnit.SECONDS);
//            }
//
//            return null;
//        }).when(eventPublisherPort).publish(anyString(), anyString(), anyString());
//
//        ExecutorService executorService = Executors.newFixedThreadPool(2);
//
//        try {
//            Future<?> firstWorker = executorService.submit(() -> {
//                startLatch.await();
//                problemEventRelayFacade.relay();
//                return null;
//            });
//
//            Future<?> secondWorker = executorService.submit(() -> {
//                startLatch.await();
//                problemEventRelayFacade.relay();
//                return null;
//            });
//
//            // when
//            startLatch.countDown();
//
//            assertThat(firstPublishEntered.await(2, TimeUnit.SECONDS)).isTrue();
//
//            Thread.sleep(300);
//            releaseFirstPublish.countDown();
//
//            firstWorker.get(5, TimeUnit.SECONDS);
//            secondWorker.get(5, TimeUnit.SECONDS);
//
//            // then
//            OutboxState state = readOutbox(outboxId);
//
//            assertThat(publishCount.get()).isEqualTo(1);
//            assertThat(state.status()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
//
//            verify(eventPublisherPort, times(1)).publish(
//                    eq(TOPIC),
//                    eq(outbox.getAggregateId().toString()),
//                    anyString()
//            );
//        } finally {
//            releaseFirstPublish.countDown();
//            executorService.shutdownNow();
//        }
//    }

    private ProblemEventOutbox savePendingOutbox() {
        UUID eventId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();

        ProblemEventOutbox outbox = ProblemEventOutbox.createPending(
                eventId,
                problemId,
                1,
                createPayload(eventId, problemId, problemVersionId),
                Instant.parse("2026-09-23T00:00:00Z")
        );

        return problemEventOutboxRepository.save(outbox);
    }

    private void setNextAttemptAtToPast(UUID outboxId) {
        jdbcTemplate.update(
                """
                UPDATE content_schema.p_problem_event_outboxes
                SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE id = ?
                """,
                outboxId
        );
    }

    private void setNextAttemptAtToFuture(UUID outboxId) {
        jdbcTemplate.update(
                """
                UPDATE content_schema.p_problem_event_outboxes
                SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 hour'
                WHERE id = ?
                """,
                outboxId
        );
    }

    private OutboxState readOutbox(UUID outboxId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status,
                       retry_count,
                       next_attempt_at,
                       published_at,
                       last_error,
                       lock_version
                FROM content_schema.p_problem_event_outboxes
                WHERE id = ?
                """,
                (rs, rowNum) -> {
                    OffsetDateTime nextAttemptAt = rs.getObject("next_attempt_at", OffsetDateTime.class);
                    OffsetDateTime publishedAt = rs.getObject("published_at", OffsetDateTime.class);

                    return new OutboxState(
                            ProblemEventOutboxStatus.valueOf(rs.getString("status")),
                            rs.getInt("retry_count"),
                            nextAttemptAt == null ? null : nextAttemptAt.toInstant(),
                            publishedAt == null ? null : publishedAt.toInstant(),
                            rs.getString("last_error"),
                            rs.getLong("lock_version")
                    );
                },
                outboxId
        );
    }

    private String createPayload(UUID eventId, UUID problemId, UUID problemVersionId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "PROBLEM_PUBLISHED",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-23T00:00:00Z",
                  "problemId": "%s",
                  "problemVersionId": "%s",
                  "versionNo": 1,
                  "language": "JAVA",
                  "starterCode": "class Solution {}",
                  "testCases": [],
                  "timeLimit": 1000,
                  "memoryLimit": 128,
                  "publishedAt": "2026-09-23T00:00:00Z"
                }
                """.formatted(eventId, problemId, problemVersionId);
    }

    private record OutboxState(
            ProblemEventOutboxStatus status,
            int retryCount,
            Instant nextAttemptAt,
            Instant publishedAt,
            String lastError,
            long lockVersion
    ) {
    }
}