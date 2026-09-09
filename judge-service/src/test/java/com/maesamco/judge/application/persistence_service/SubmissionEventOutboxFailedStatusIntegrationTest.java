package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.command_service.SubmissionCommandService;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class SubmissionEventOutboxFailedStatusIntegrationTest {

    private static final int MAX_RELAY_ATTEMPTS = 5;

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
    private SubmissionEventOutboxPersistenceService submissionEventOutboxPersistenceService;

    @Autowired
    private SubmissionCommandService submissionCommandService;

    @Autowired
    private SubmissionEventOutboxRepository submissionEventOutboxRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

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

    private UUID createSubmission(String idempotencyKey) {
        SubmissionCreateResult result = submissionCommandService.submit(new SubmissionCreateCommand(
                userId, idempotencyKey, problemId, "public class Main {}", "JAVA17"));
        return result.submissionId();
    }

    @Test
    @DisplayName("재시도 상한 도달 시 Outbox가 실제 PostgreSQL에 FAILED로 저장된다 (CHECK 제약조건 위반 없음)")
    void savesOutboxAsFailedWhenRetryLimitReached() {
        // given
        UUID submissionId = createSubmission("idem-key-retry-cap");
        SubmissionEventOutbox outbox = submissionEventOutboxRepository.saveAndFlush(
                SubmissionEventOutbox.create(submissionId, "JudgeRequested", "{}"));
        UUID outboxId = outbox.getId();

        // when
        for (int attempt = 1; attempt <= MAX_RELAY_ATTEMPTS; attempt++) {
            SubmissionEventOutbox freshOutbox =
                    submissionEventOutboxRepository.findById(outboxId).orElseThrow();
            submissionEventOutboxPersistenceService.recordFailedAttempt(freshOutbox);
        }

        // then
        SubmissionEventOutbox reloadedOutbox =
                submissionEventOutboxRepository.findById(outboxId).orElseThrow();
        assertThat(reloadedOutbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(reloadedOutbox.getAttemptCount()).isEqualTo(MAX_RELAY_ATTEMPTS);

        Submission reloadedSubmission = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(reloadedSubmission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(reloadedSubmission.getFailureCode()).isEqualTo(FailureCode.KAFKA_PROCESSING_FAILURE);
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입 처리 시 Outbox가 실제 PostgreSQL에 즉시 FAILED로 저장된다")
    void savesOutboxAsFailedWhenEventTypeUnsupported() {
        // given
        UUID submissionId = createSubmission("idem-key-unsupported-type");
        SubmissionEventOutbox outbox = submissionEventOutboxRepository.saveAndFlush(
                SubmissionEventOutbox.create(submissionId, "SubmissionJudged", "{}"));
        UUID outboxId = outbox.getId();

        // when
        submissionEventOutboxPersistenceService.markUnsupportedEventType(outbox);

        // then
        SubmissionEventOutbox reloadedOutbox =
                submissionEventOutboxRepository.findById(outboxId).orElseThrow();
        assertThat(reloadedOutbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(reloadedOutbox.getAttemptCount()).isZero();

        Submission reloadedSubmission = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(reloadedSubmission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(reloadedSubmission.getFailureCode()).isEqualTo(FailureCode.INTERNAL_SYSTEM_ERROR);
    }
}