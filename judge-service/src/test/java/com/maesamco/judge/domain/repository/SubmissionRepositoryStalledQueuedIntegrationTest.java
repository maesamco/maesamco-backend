package com.maesamco.judge.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 이슈 #350 — QUEUED 정체 복구가 쓰는 조회가 실제 PostgreSQL에서 의도대로 동작하는지 확인한다. */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SubmissionRepository 정체된 QUEUED 조회")
class SubmissionRepositoryStalledQueuedIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // 이 테스트가 만든 QUEUED 제출을 복구 스케줄러가 먼저 집어가지 않도록 끈다.
        registry.add("judge.queued-recovery.enabled", () -> "false");
    }

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Submission saveQueued(String key) {
        Submission submission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, key);
        submission.markQueued();
        return submissionRepository.saveAndFlush(submission);
    }

    private void setUpdatedAt(UUID id, Instant updatedAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("update judge_schema.p_submissions set updated_at = :at where id = :id")
                .setParameter("at", updatedAt)
                .setParameter("id", id)
                .executeUpdate());
    }

    @Test
    @DisplayName("QUEUED이면서 기준 시각보다 오래 갱신되지 않은 제출만, 오래된 제출부터 조회한다")
    void findsOnlyStaleQueuedSubmissionsOldestFirst() {
        Instant now = Instant.now();
        Submission staleOlder = saveQueued("stale-older-" + UUID.randomUUID());
        Submission staleNewer = saveQueued("stale-newer-" + UUID.randomUUID());
        Submission fresh = saveQueued("fresh-" + UUID.randomUUID());
        Submission pending = submissionRepository.saveAndFlush(Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, "pending-" + UUID.randomUUID()));
        setUpdatedAt(staleOlder.getId(), now.minusSeconds(600));
        setUpdatedAt(staleNewer.getId(), now.minusSeconds(300));
        setUpdatedAt(fresh.getId(), now);
        setUpdatedAt(pending.getId(), now.minusSeconds(600));
        entityManager.clear();

        List<Submission> stalled = submissionRepository.findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
                SubmissionStatus.QUEUED, now.minusSeconds(60), PageRequest.of(0, 50));

        List<UUID> ids = stalled.stream().map(Submission::getId).toList();
        assertThat(ids).contains(staleOlder.getId(), staleNewer.getId());
        assertThat(ids).doesNotContain(fresh.getId(), pending.getId());
        assertThat(ids.indexOf(staleOlder.getId())).isLessThan(ids.indexOf(staleNewer.getId()));
    }
}
