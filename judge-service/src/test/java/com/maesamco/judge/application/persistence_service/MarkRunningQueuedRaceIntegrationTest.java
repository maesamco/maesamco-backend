package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 이슈 #350 — 운영에서 실제로 발생한 경합(Outbox 릴레이의 PENDING→QUEUED 커밋 ↔ Consumer의 RUNNING 전이)을
 * 서로 독립된 두 트랜잭션/스레드로 재현해, 실제 PostgreSQL에서 @Version 충돌이 나고 최신 상태를 다시 읽으면 RUNNING으로
 * 수렴하는지 확인한다. mock 단위 테스트로는 transaction boundary와 flush 타이밍에 의존하는 이 경합을 재현할 수 없다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RUNNING 전이 ↔ 릴레이 QUEUED 전이 경합")
class MarkRunningQueuedRaceIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("judge.queued-recovery.enabled", () -> "false");
        registry.add("outbox.relay.fixed-delay-ms", () -> "3600000");
    }

    @Autowired
    private JudgeExecutionPersistenceService judgeExecutionPersistenceService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Submission newPendingSubmission() {
        return submissionRepository.saveAndFlush(Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, "race-" + UUID.randomUUID()));
    }

    @Test
    @DisplayName("릴레이가 같은 제출을 읽은 채 Consumer의 RUNNING 전이가 먼저 커밋되면 릴레이의 QUEUED 커밋이 낙관적 락으로 실패한다")
    void relayLosesAgainstConsumerAndConflictSurfaces() throws Exception {
        UUID id = newPendingSubmission().getId();
        CountDownLatch relayHasRead = new CountDownLatch(1);
        CountDownLatch consumerCommitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 릴레이: PENDING을 읽어 두고(Kafka 발행 중), Consumer가 커밋한 뒤에 QUEUED로 바꿔 커밋하려 한다.
            Future<?> relay = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                Submission submission = submissionRepository.findById(id).orElseThrow();
                relayHasRead.countDown();
                await(consumerCommitted);
                submission.markQueued();
                submissionRepository.save(submission);
            }));

            assertThat(relayHasRead.await(10, TimeUnit.SECONDS)).isTrue();
            // Consumer: 같은 제출을 RUNNING으로 전이해 먼저 커밋한다.
            Optional<UUID> marked = judgeExecutionPersistenceService.markRunningIfNeeded(id);
            consumerCommitted.countDown();

            assertThat(marked).contains(id);
            assertThatThrownBy(() -> relay.get(10, TimeUnit.SECONDS))
                    .satisfies(e -> assertThat(e.getCause())
                            .isInstanceOf(ObjectOptimisticLockingFailureException.class));
            assertThat(submissionRepository.findById(id).orElseThrow().getStatus())
                    .isEqualTo(SubmissionStatus.RUNNING);
        } finally {
            consumerCommitted.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Consumer가 PENDING을 읽은 채 릴레이의 QUEUED 전이가 먼저 커밋되면 Consumer가 충돌하고, 다시 읽으면 RUNNING으로 수렴한다")
    void consumerConflictsWithRelayAndConvergesOnRetry() throws Exception {
        UUID id = newPendingSubmission().getId();
        CountDownLatch consumerHasRead = new CountDownLatch(1);
        CountDownLatch relayCommitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // Consumer: PENDING을 읽어 둔 채 릴레이의 QUEUED 커밋을 기다린 뒤 RUNNING을 시도한다(첫 시도 = 충돌).
            Future<?> firstAttempt = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                Submission submission = submissionRepository.findById(id).orElseThrow();
                consumerHasRead.countDown();
                await(relayCommitted);
                submission.markRunning();
                submissionRepository.save(submission);
            }));

            assertThat(consumerHasRead.await(10, TimeUnit.SECONDS)).isTrue();
            // 릴레이: PENDING → QUEUED를 먼저 커밋한다.
            transactionTemplate.executeWithoutResult(status -> {
                Submission submission = submissionRepository.findById(id).orElseThrow();
                submission.markQueued();
                submissionRepository.save(submission);
            });
            relayCommitted.countDown();

            assertThatThrownBy(() -> firstAttempt.get(10, TimeUnit.SECONDS))
                    .satisfies(e -> assertThat(e.getCause())
                            .isInstanceOf(ObjectOptimisticLockingFailureException.class));

            // #350의 수정: 충돌 뒤 최신 상태(QUEUED)를 다시 읽어 재시도하면 RUNNING으로 전이된다.
            Optional<UUID> retried = judgeExecutionPersistenceService.markRunningIfNeeded(id);

            assertThat(retried).contains(id);
            assertThat(submissionRepository.findById(id).orElseThrow().getStatus())
                    .isEqualTo(SubmissionStatus.RUNNING);
        } finally {
            relayCommitted.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
