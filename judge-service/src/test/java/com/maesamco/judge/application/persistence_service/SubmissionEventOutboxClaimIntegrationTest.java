package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.command_service.SubmissionCommandService;
import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 이슈 #272 — Outbox 선점(claim)이 실제 PostgreSQL에서 다중 인스턴스 동시 실행에도 같은 행을 두 번 집지 않는지,
 * lease 만료 재선점과 fencing(늦게 도착한 이전 Worker의 결과 무시)이 동작하는지 확인한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Submission Outbox 선점(claim)")
class SubmissionEventOutboxClaimIntegrationTest {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // 이 테스트가 만든 Outbox를 Relay 스케줄러가 먼저 선점하지 않도록 폴링 주기를 사실상 끈다.
        registry.add("outbox.relay.fixed-delay-ms", () -> "3600000");
    }

    @Autowired
    private SubmissionEventOutboxPersistenceService persistenceService;

    @Autowired
    private SubmissionEventOutboxRepository outboxRepository;

    @Autowired
    private SubmissionCommandService submissionCommandService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ProblemExecutionSpecRepository problemExecutionSpecRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID userId;
    private UUID problemId;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 남긴 행이 선점 대상에 섞이지 않게 비운다.
        outboxRepository.deleteAll();
        userId = UUID.randomUUID();
        problemId = UUID.randomUUID();
        problemExecutionSpecRepository.saveAndFlush(ProblemExecutionSpec.fromPublishedEvent(
                problemId, UUID.randomUUID(), SubmissionLanguage.JAVA17,
                "public class Main {}", "[]", 1000, 128, Instant.now()));
    }

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        submissionRepository.deleteAll();
        problemExecutionSpecRepository.deleteAll();
    }

    /** 실제 제출을 만든 뒤(요청 흐름이 Outbox를 함께 저장한다) 그 Outbox를 돌려준다. */
    private SubmissionEventOutbox newOutbox() {
        UUID submissionId = submissionCommandService.submit(new SubmissionCreateCommand(
                userId, "idem-" + UUID.randomUUID(), problemId, "public class Main {}", "JAVA17")).submissionId();
        return outboxRepository.findAll().stream()
                .filter(o -> o.getAggregateId().equals(submissionId))
                .findFirst().orElseThrow();
    }

    private void expireLease(UUID outboxId) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("update judge_schema.p_submission_event_outboxes "
                        + "set lease_until = now() - interval '1 second' where id = :id")
                .setParameter("id", outboxId)
                .executeUpdate());
    }

    @Test
    @DisplayName("PENDING을 선점하면 IN_PROGRESS + claimId + lease가 기록된다")
    void claimMarksInProgressWithLease() {
        SubmissionEventOutbox outbox = newOutbox();
        UUID claimId = UUID.randomUUID();

        SubmissionEventOutbox claimed = persistenceService.claimNext(claimId, LEASE).orElseThrow();

        assertThat(claimed.getId()).isEqualTo(outbox.getId());
        SubmissionEventOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
        assertThat(reloaded.getClaimId()).isEqualTo(claimId);
        assertThat(reloaded.getLeaseUntil()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("이미 선점된(lease 유효) 행은 다시 선점되지 않는다")
    void doesNotClaimActiveClaim() {
        newOutbox();
        persistenceService.claimNext(UUID.randomUUID(), LEASE).orElseThrow();

        Optional<SubmissionEventOutbox> second = persistenceService.claimNext(UUID.randomUUID(), LEASE);

        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("여러 인스턴스가 동시에 선점해도 각 Outbox는 정확히 한 번만 선점된다")
    void concurrentClaimersNeverClaimSameOutboxTwice() throws Exception {
        int outboxCount = 12;
        Set<UUID> created = new HashSet<>();
        for (int i = 0; i < outboxCount; i++) {
            created.add(newOutbox().getId());
        }

        int workers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<UUID>>> futures = new ArrayList<>();
        try {
            for (int w = 0; w < workers; w++) {
                Callable<List<UUID>> task = () -> {
                    List<UUID> mine = new ArrayList<>();
                    ready.countDown();
                    go.await();
                    // 더 이상 선점할 행이 없을 때까지 각 Worker가 계속 집는다(Relay 배치 루프와 동일).
                    while (true) {
                        Optional<SubmissionEventOutbox> claimed = persistenceService.claimNext(UUID.randomUUID(), LEASE);
                        if (claimed.isEmpty()) {
                            return mine;
                        }
                        mine.add(claimed.get().getId());
                    }
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<UUID> all = new ArrayList<>();
            for (Future<List<UUID>> f : futures) {
                all.addAll(f.get(30, TimeUnit.SECONDS));
            }

            assertThat(all).hasSize(outboxCount);              // 누락 없이 전부 선점되고
            assertThat(new HashSet<>(all)).hasSize(outboxCount); // 중복 선점이 없다
            assertThat(new HashSet<>(all)).isEqualTo(created);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("다른 트랜잭션이 잠근 행은 기다리지 않고 건너뛴다(SKIP LOCKED)")
    void skipsRowLockedByAnotherTransactionWithoutBlocking() throws Exception {
        SubmissionEventOutbox outbox = newOutbox();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 다른 인스턴스가 선점 트랜잭션 중이라 행을 FOR UPDATE로 잡고 있는 상황.
            Future<?> holder = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                outboxRepository.findClaimableForUpdate(
                        OutboxStatus.PENDING, OutboxStatus.IN_PROGRESS, Instant.now(),
                        org.springframework.data.domain.PageRequest.of(0, 1));
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            long startedAt = System.nanoTime();
            Optional<SubmissionEventOutbox> claimed = persistenceService.claimNext(UUID.randomUUID(), LEASE);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            assertThat(claimed).isEmpty();
            assertThat(elapsedMs).isLessThan(3_000L); // 잠금이 풀리기를 기다렸다면 30초 가까이 걸린다.
            assertThat(outboxRepository.findById(outbox.getId()).orElseThrow().getStatus())
                    .isEqualTo(OutboxStatus.PENDING);

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("lease가 만료된 IN_PROGRESS(죽은 Worker) 행은 다른 Worker가 재선점한다")
    void reclaimsExpiredLease() {
        SubmissionEventOutbox outbox = newOutbox();
        UUID deadWorker = UUID.randomUUID();
        persistenceService.claimNext(deadWorker, LEASE).orElseThrow();
        expireLease(outbox.getId());

        UUID newWorker = UUID.randomUUID();
        SubmissionEventOutbox reclaimed = persistenceService.claimNext(newWorker, LEASE).orElseThrow();

        assertThat(reclaimed.getId()).isEqualTo(outbox.getId());
        assertThat(outboxRepository.findById(outbox.getId()).orElseThrow().getClaimId()).isEqualTo(newWorker);
    }

    @Test
    @DisplayName("재선점된 뒤 늦게 도착한 이전 Worker의 결과는 무시되고 새 Worker의 선점이 유지된다(fencing)")
    void staleWorkerResultIsIgnoredAfterReclaim() {
        SubmissionEventOutbox outbox = newOutbox();
        UUID oldWorker = UUID.randomUUID();
        persistenceService.claimNext(oldWorker, LEASE).orElseThrow();
        expireLease(outbox.getId());
        UUID newWorker = UUID.randomUUID();
        persistenceService.claimNext(newWorker, LEASE).orElseThrow();

        boolean published = persistenceService.markPublished(outbox.getId(), oldWorker);
        persistenceService.recordFailedAttempt(outbox.getId(), oldWorker);

        assertThat(published).isFalse();
        SubmissionEventOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
        assertThat(reloaded.getClaimId()).isEqualTo(newWorker);
        assertThat(reloaded.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("발행 실패를 기록하면 선점이 풀려 PENDING으로 돌아가 다시 선점할 수 있다")
    void failedAttemptReleasesClaimForRetry() {
        SubmissionEventOutbox outbox = newOutbox();
        UUID claimId = UUID.randomUUID();
        persistenceService.claimNext(claimId, LEASE).orElseThrow();

        persistenceService.recordFailedAttempt(outbox.getId(), claimId);

        SubmissionEventOutbox reloaded = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getClaimId()).isNull();
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(persistenceService.claimNext(UUID.randomUUID(), LEASE)).isPresent();
    }
}
