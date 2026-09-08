package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
class SubmissionEventOutboxPersistenceServiceIntegrationTest {

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
    private SubmissionEventOutboxRepository submissionEventOutboxRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @AfterEach
    void tearDown() {
        submissionEventOutboxRepository.deleteAll();
        submissionRepository.deleteAll();
    }

    @Test
    @DisplayName("markPublished 도중 Submission 전이가 실패하면 트랜잭션이 롤백되어 Outbox는 실제로는 PENDING으로 남는다")
    void rollsBackOutboxWhenSubmissionTransitionFails() {
        // given
        UUID missingSubmissionId = UUID.randomUUID();
        SubmissionEventOutbox outbox =
                SubmissionEventOutbox.create(missingSubmissionId, "JudgeRequested", "{}");
        submissionEventOutboxRepository.saveAndFlush(outbox);
        UUID outboxId = outbox.getId();

        // when
        assertThatThrownBy(() -> submissionEventOutboxPersistenceService.markPublished(outbox))
                .isInstanceOf(BusinessException.class);

        // then 1
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);

        // then 2
        SubmissionEventOutbox reloaded = submissionEventOutboxRepository.findById(outboxId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttemptCount()).isZero();

        // when 2
        submissionEventOutboxPersistenceService.recordPostPublishFailure(outboxId);

        // then 3
        SubmissionEventOutbox afterRetryRecorded = submissionEventOutboxRepository.findById(outboxId).orElseThrow();
        assertThat(afterRetryRecorded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterRetryRecorded.getAttemptCount()).isEqualTo(1);
    }
}