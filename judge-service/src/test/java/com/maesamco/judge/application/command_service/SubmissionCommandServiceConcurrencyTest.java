package com.maesamco.judge.application.command_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SubmissionCommandServiceConcurrencyTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SubmissionCommandService submissionCommandService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionEventOutboxRepository submissionEventOutboxRepository;

    @Autowired
    private ProblemExecutionSpecRepository problemExecutionSpecRepository;

    private UUID userId;
    private UUID problemId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        problemId = UUID.randomUUID();

        ProblemExecutionSpec spec = ProblemExecutionSpec.fromPublishedEvent(
                problemId, UUID.randomUUID(), SubmissionLanguage.JAVA17,
                "public class Main {}", "[]", 1000, 128, Instant.now()
        );
        problemExecutionSpecRepository.saveAndFlush(spec);
    }

    @AfterEach
    void tearDown() {
        submissionEventOutboxRepository.deleteAll();
        submissionRepository.deleteAll();
        problemExecutionSpecRepository.deleteAll();
    }

    @Test
    void concurrentSubmissionsGetDistinctAttemptNo() throws InterruptedException {
        // given — 같은 (userId, problemId)로 서로 다른 Idempotency-Key를 가진 두 요청이 동시에 들어온다
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<SubmissionCreateResult> results;

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            List<Future<SubmissionCreateResult>> futures = List.of(
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();
                        return submissionCommandService.submit(new SubmissionCreateCommand(
                                userId, "idem-key-A", problemId, "public class Main {}", "JAVA17"));
                    }),
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();
                        return submissionCommandService.submit(new SubmissionCreateCommand(
                                userId, "idem-key-B", problemId, "public class Main {}", "JAVA17"));
                    })
            );

            boolean bothReady = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(bothReady).as("두 스레드가 제시간 안에 준비되지 못했다").isTrue();
            startLatch.countDown(); // 두 스레드를 최대한 동시에 출발시켜 경합을 강제로 유발

            // when
            results = futures.stream()
                    .map(f -> {
                        try {
                            return f.get(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("테스트 스레드가 인터럽트됨", e);
                        } catch (ExecutionException | TimeoutException e) {
                            throw new IllegalStateException("제출 처리 중 예외 발생", e);
                        }
                    })
                    .toList();
        }

        // then — 둘 다 예외 없이 성공하고, attemptNo가 서로 겹치지 않는다
        List<Submission> saved = submissionRepository.findAll();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(Submission::getAttemptNo).containsExactlyInAnyOrder(1, 2);
        assertThat(submissionEventOutboxRepository.findAll()).hasSize(2);
        assertThat(results).extracting(SubmissionCreateResult::submissionId).doesNotHaveDuplicates();
    }
}