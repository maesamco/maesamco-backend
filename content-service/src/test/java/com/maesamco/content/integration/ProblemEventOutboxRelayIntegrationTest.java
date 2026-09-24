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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final long LEASE_DURATION_MS = 60_000L;
    private static final long PUBLISH_TIMEOUT_MS = 5_000L;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private EventPublisherPort eventPublisherPort;
    private ProblemEventRelayFacade problemEventRelayFacade;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM content_schema.p_problem_event_outboxes");

        eventPublisherPort = mock(EventPublisherPort.class);

        problemEventRelayFacade = createRelay();
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
        // 선점(+1)과 PUBLISHED 기록(+1)으로 두 번 갱신됩니다.
        assertThat(after.lockVersion()).isEqualTo(before.lockVersion() + 2);
        assertThat(after.claimId()).isNull();
        assertThat(after.leaseUntil()).isNull();
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
    @DisplayName("Kafka 발행이 실패하면 PENDING 상태로 되돌리고 retryCount와 nextAttemptAt을 갱신한다")
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
        // 선점(+1)과 실패 기록(+1)으로 두 번 갱신되며, 선점은 해제됩니다.
        assertThat(state.lockVersion()).isEqualTo(2L);
        assertThat(state.claimId()).isNull();
        assertThat(state.leaseUntil()).isNull();
    }

    @Test
    @DisplayName("두 Relay 인스턴스가 동시에 같은 Outbox를 relay해도 Kafka 발행은 한 번만 수행한다")
    void relay_concurrently_sameOutbox_publishesOnlyOnce() throws Exception {
        // given
        ProblemEventOutbox outbox = savePendingOutbox();
        UUID outboxId = outbox.getId();

        ProblemEventRelayFacade instanceA = createRelay();
        ProblemEventRelayFacade instanceB = createRelay();

        AtomicInteger publishCount = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch firstPublishEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstPublish = new CountDownLatch(1);

        // 첫 번째 발행을 Kafka ACK 대기 중인 것처럼 붙잡아 두고, 그동안 다른 인스턴스가 relay하도록 합니다.
        doAnswer(invocation -> {
            int currentCount = publishCount.incrementAndGet();

            if (currentCount == 1) {
                firstPublishEntered.countDown();
                releaseFirstPublish.await(5, TimeUnit.SECONDS);
            }

            return null;
        }).when(eventPublisherPort).publish(anyString(), anyString(), anyString());

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstWorker = executorService.submit(() -> {
                startLatch.await();
                instanceA.relay();
                return null;
            });

            Future<?> secondWorker = executorService.submit(() -> {
                startLatch.await();
                instanceB.relay();
                return null;
            });

            // when
            startLatch.countDown();

            assertThat(firstPublishEntered.await(5, TimeUnit.SECONDS)).isTrue();

            // 첫 번째 발행이 ACK 대기 중인 동안 나머지 인스턴스의 relay가 끝나기를 기다립니다.
            Thread.sleep(300);
            releaseFirstPublish.countDown();

            firstWorker.get(10, TimeUnit.SECONDS);
            secondWorker.get(10, TimeUnit.SECONDS);

            // then
            OutboxState state = readOutbox(outboxId);

            assertThat(publishCount.get()).isEqualTo(1);
            assertThat(state.status()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
            assertThat(state.claimId()).isNull();
            assertThat(state.leaseUntil()).isNull();

            verify(eventPublisherPort, times(1)).publish(
                    eq(TOPIC),
                    eq(outbox.getAggregateId().toString()),
                    anyString()
            );
        } finally {
            releaseFirstPublish.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("여러 Relay 인스턴스가 동시에 여러 Outbox를 처리해도 각 Outbox는 정확히 한 번씩 발행된다")
    void relay_multipleInstances_manyOutboxes_eachPublishedExactlyOnce() throws Exception {
        // given
        int outboxCount = 30;
        int instanceCount = 3;

        List<UUID> aggregateIds = new ArrayList<>();
        for (int i = 0; i < outboxCount; i++) {
            aggregateIds.add(savePendingOutbox().getAggregateId());
        }

        Map<String, AtomicInteger> publishCountByKey = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            String key = invocation.getArgument(1);
            publishCountByKey.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
            Thread.sleep(5);
            return null;
        }).when(eventPublisherPort).publish(anyString(), anyString(), anyString());

        ExecutorService executorService = Executors.newFixedThreadPool(instanceCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < instanceCount; i++) {
                ProblemEventRelayFacade instance = createRelay();
                workers.add(executorService.submit(() -> {
                    startLatch.await();
                    instance.relay();
                    return null;
                }));
            }

            // when
            startLatch.countDown();
            for (Future<?> worker : workers) {
                worker.get(30, TimeUnit.SECONDS);
            }

            // then
            assertThat(publishCountByKey).hasSize(outboxCount);
            assertThat(publishCountByKey.values())
                    .allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
            assertThat(aggregateIds)
                    .allSatisfy(id -> assertThat(publishCountByKey).containsKey(id.toString()));

            Integer publishedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM content_schema.p_problem_event_outboxes WHERE status = 'PUBLISHED'",
                    Integer.class
            );
            assertThat(publishedCount).isEqualTo(outboxCount);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("다른 트랜잭션이 선점 중(행 잠금)인 Outbox는 대기하지 않고 건너뛴다 (FOR UPDATE SKIP LOCKED)")
    void claimNext_rowLockedByOtherTransaction_skipsWithoutBlocking() throws Exception {
        // given
        ProblemEventOutbox locked = savePendingOutbox(Instant.parse("2026-09-23T00:00:00Z"));
        ProblemEventOutbox free = savePendingOutbox(Instant.parse("2026-09-23T00:00:01Z"));

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            // 인스턴스 A: 가장 오래된 Outbox 행을 잠근 채 트랜잭션을 유지합니다.
            Future<?> lockHolder = executorService.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                List<ProblemEventOutbox> rows =
                        problemEventOutboxRepository.findClaimableForUpdate(Instant.now(), 1);
                assertThat(rows).extracting(ProblemEventOutbox::getId).containsExactly(locked.getId());
                lockAcquired.countDown();
                try {
                    releaseLock.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            // when: 인스턴스 B가 선점을 시도합니다.
            long startedAt = System.nanoTime();
            Optional<ProblemEventOutbox> claimed = problemEventOutboxPersistenceService.claimNext(
                    UUID.randomUUID(),
                    Duration.ofMillis(LEASE_DURATION_MS)
            );
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);

            // then: 잠긴 행을 기다리지 않고 다음 행을 선점합니다.
            assertThat(claimed).isPresent();
            assertThat(claimed.get().getId()).isEqualTo(free.getId());
            assertThat(elapsedMillis).isLessThan(2_000L);
            assertThat(readOutbox(locked.getId()).status()).isEqualTo(ProblemEventOutboxStatus.PENDING);
            assertThat(readOutbox(free.getId()).status()).isEqualTo(ProblemEventOutboxStatus.IN_PROGRESS);
        } finally {
            releaseLock.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("선점한 인스턴스가 종료되어도 lease 만료 후 다른 인스턴스가 재선점해 발행하므로 이벤트가 유실되지 않는다")
    void relay_crashedInstance_leaseExpires_otherInstancePublishes() {
        // given: 인스턴스 A가 선점한 뒤 결과를 기록하지 못하고 종료되었다고 가정합니다.
        ProblemEventOutbox outbox = savePendingOutbox();
        UUID outboxId = outbox.getId();
        UUID crashedClaimId = UUID.randomUUID();

        problemEventOutboxPersistenceService.claimNext(crashedClaimId, Duration.ofMillis(LEASE_DURATION_MS));

        // lease가 남아있는 동안에는 다른 인스턴스가 가져가지 않습니다.
        createRelay().relay();
        verifyNoInteractions(eventPublisherPort);
        assertThat(readOutbox(outboxId).claimId()).isEqualTo(crashedClaimId);

        expireLease(outboxId);

        // when: 인스턴스 B가 relay합니다.
        createRelay().relay();

        // then
        OutboxState state = readOutbox(outboxId);

        verify(eventPublisherPort, times(1)).publish(
                eq(TOPIC),
                eq(outbox.getAggregateId().toString()),
                anyString()
        );
        assertThat(state.status()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
        assertThat(state.claimId()).isNull();
    }

    @Test
    @DisplayName("lease 만료로 재선점된 뒤 도착한 이전 Worker의 결과는 claimId 불일치로 무시된다 (fencing)")
    void staleWorkerResult_afterReclaim_isIgnored() {
        // given
        ProblemEventOutbox outbox = savePendingOutbox();
        UUID outboxId = outbox.getId();
        UUID staleClaimId = UUID.randomUUID();
        UUID newClaimId = UUID.randomUUID();

        problemEventOutboxPersistenceService.claimNext(staleClaimId, Duration.ofMillis(LEASE_DURATION_MS));
        expireLease(outboxId);
        problemEventOutboxPersistenceService.claimNext(newClaimId, Duration.ofMillis(LEASE_DURATION_MS));

        // when: 이전 Worker가 늦게 실패/성공 결과를 기록하려 합니다.
        boolean staleFailure = problemEventOutboxPersistenceService.recordFailedAttempt(
                outboxId, staleClaimId, "KAFKA_PUBLISH_FAILED:IllegalStateException"
        );
        boolean stalePublished = problemEventOutboxPersistenceService.markPublished(outboxId, staleClaimId);

        // then
        OutboxState state = readOutbox(outboxId);

        assertThat(staleFailure).isFalse();
        assertThat(stalePublished).isFalse();
        assertThat(state.status()).isEqualTo(ProblemEventOutboxStatus.IN_PROGRESS);
        assertThat(state.claimId()).isEqualTo(newClaimId);
        assertThat(state.retryCount()).isZero();

        // 현재 선점자는 정상적으로 결과를 기록합니다.
        assertThat(problemEventOutboxPersistenceService.markPublished(outboxId, newClaimId)).isTrue();
        assertThat(readOutbox(outboxId).status()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
    }

    private ProblemEventRelayFacade createRelay() {
        return new ProblemEventRelayFacade(
                problemEventOutboxPersistenceService,
                eventPublisherPort,
                TOPIC,
                BATCH_SIZE,
                MAX_PAYLOAD_BYTES,
                LEASE_DURATION_MS,
                PUBLISH_TIMEOUT_MS
        );
    }

    private void expireLease(UUID outboxId) {
        jdbcTemplate.update(
                """
                UPDATE content_schema.p_problem_event_outboxes
                SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """,
                outboxId
        );
    }

    private ProblemEventOutbox savePendingOutbox() {
        return savePendingOutbox(Instant.parse("2026-09-23T00:00:00Z"));
    }

    private ProblemEventOutbox savePendingOutbox(Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();

        ProblemEventOutbox outbox = ProblemEventOutbox.createPending(
                eventId,
                problemId,
                1,
                createPayload(eventId, problemId, problemVersionId),
                occurredAt
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
                       lock_version,
                       claim_id,
                       lease_until
                FROM content_schema.p_problem_event_outboxes
                WHERE id = ?
                """,
                (rs, rowNum) -> {
                    OffsetDateTime nextAttemptAt = rs.getObject("next_attempt_at", OffsetDateTime.class);
                    OffsetDateTime publishedAt = rs.getObject("published_at", OffsetDateTime.class);
                    OffsetDateTime leaseUntil = rs.getObject("lease_until", OffsetDateTime.class);

                    return new OutboxState(
                            ProblemEventOutboxStatus.valueOf(rs.getString("status")),
                            rs.getInt("retry_count"),
                            nextAttemptAt == null ? null : nextAttemptAt.toInstant(),
                            publishedAt == null ? null : publishedAt.toInstant(),
                            rs.getString("last_error"),
                            rs.getLong("lock_version"),
                            rs.getObject("claim_id", UUID.class),
                            leaseUntil == null ? null : leaseUntil.toInstant()
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
            long lockVersion,
            UUID claimId,
            Instant leaseUntil
    ) {
    }
}