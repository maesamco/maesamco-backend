package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({JpaAuditingConfig.class, ProblemEventOutboxRepositoryImpl.class})
@EnableJpaRepositories(basePackageClasses = {SpringDataProblemEventOutboxRepository.class})
class ProblemEventOutboxRepositoryTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    @Test
    @DisplayName("ProblemEventOutbox를 저장하면 payload가 PostgreSQL JSONB로 저장되고 다시 조회된다")
    void saveAndFind_restoresJsonbPayload() {
        // given
        UUID eventId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-21T00:00:00Z");

        ProblemEventOutbox outbox = ProblemEventOutbox.createPending(
                eventId,
                problemId,
                1,
                createPayload(eventId, problemId, problemVersionId),
                occurredAt
        );

        // when
        ProblemEventOutbox saved = problemEventOutboxRepository.save(outbox);

        entityManager.flush();

        UUID outboxId = saved.getId();

        entityManager.clear();

        ProblemEventOutbox found = problemEventOutboxRepository.findById(outboxId).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(outboxId);
        assertThat(found.getEventId()).isEqualTo(eventId);
        assertThat(found.getAggregateType()).isEqualTo("PROBLEM");
        assertThat(found.getAggregateId()).isEqualTo(problemId);
        assertThat(found.getEventType()).isEqualTo("PROBLEM_PUBLISHED");
        assertThat(found.getEventVersion()).isEqualTo(1);
        assertThat(found.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
        assertThat(found.getRetryCount()).isZero();
        assertThat(found.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(found.getPublishedAt()).isNull();
        assertThat(found.getLastError()).isNull();
        assertThat(found.getPayload()).contains(problemVersionId.toString());

        String columnType = (String) entityManager.createNativeQuery(
                """
                SELECT pg_typeof(payload)::text
                FROM content_schema.p_problem_event_outboxes
                WHERE id = :outboxId
                """
        ).setParameter("outboxId", outboxId).getSingleResult();

        assertThat(columnType).isEqualTo("jsonb");

        String storedProblemVersionId = (String) entityManager.createNativeQuery(
                """
                SELECT payload ->> 'problemVersionId'
                FROM content_schema.p_problem_event_outboxes
                WHERE id = :outboxId
                """
        ).setParameter("outboxId", outboxId).getSingleResult();

        assertThat(storedProblemVersionId).isEqualTo(problemVersionId.toString());
    }

    @Test
    @DisplayName("같은 eventId의 ProblemEventOutbox를 두 번 저장하면 UNIQUE 제약으로 차단된다")
    void saveDuplicateEventId_throwsException() {
        // given
        UUID eventId = UUID.randomUUID();

        ProblemEventOutbox first = createOutbox(
                eventId,
                UUID.randomUUID(),
                Instant.parse("2026-09-21T00:00:00Z")
        );

        problemEventOutboxRepository.save(first);
        entityManager.flush();
        entityManager.clear();

        ProblemEventOutbox duplicate = createOutbox(
                eventId,
                UUID.randomUUID(),
                Instant.parse("2026-09-21T00:00:01Z")
        );

        // when & then
        assertThatThrownBy(() -> {
            problemEventOutboxRepository.save(duplicate);
            entityManager.flush();
        }).hasMessageContaining("uk_problem_event_outboxes_event_id");
    }

    @Test
    @DisplayName("polling 조회는 PENDING 상태의 Outbox만 반환한다")
    void findPollableByStatus_returnsOnlyPendingOutboxes() {
        // given
        Instant baseTime = Instant.parse("2026-09-21T00:00:00Z");

        ProblemEventOutbox pending = createOutbox(baseTime);
        ProblemEventOutbox published = createOutbox(baseTime.plusSeconds(1));
        ProblemEventOutbox failed = createOutbox(baseTime.plusSeconds(2));

        published.markPublished(baseTime.plusSeconds(10));

        problemEventOutboxRepository.save(pending);
        problemEventOutboxRepository.save(published);
        problemEventOutboxRepository.save(failed);

        entityManager.flush();

        UUID pendingId = pending.getId();
        UUID publishedId = published.getId();
        UUID failedId = failed.getId();

        setStatus(failedId, ProblemEventOutboxStatus.FAILED);

        entityManager.flush();
        entityManager.clear();

        // when
        List<ProblemEventOutbox> found = problemEventOutboxRepository.findPollableByStatus(
                ProblemEventOutboxStatus.PENDING,
                10
        );

        // then
        assertThat(found).extracting(ProblemEventOutbox::getId)
                .containsExactly(pendingId)
                .doesNotContain(publishedId, failedId);

        assertThat(found).allSatisfy(outbox -> assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING));
    }

    @Test
    @DisplayName("nextAttemptAt이 null인 PENDING Outbox는 polling 대상에 포함된다")
    void findPollableByStatus_nextAttemptAtNull_isIncluded() {
        // given
        ProblemEventOutbox outbox = createOutbox(Instant.parse("2026-09-21T00:00:00Z"));

        problemEventOutboxRepository.save(outbox);
        entityManager.flush();

        UUID outboxId = outbox.getId();

        assertThat(readNextAttemptAt(outboxId)).isNull();

        entityManager.clear();

        // when
        List<ProblemEventOutbox> found = problemEventOutboxRepository.findPollableByStatus(
                ProblemEventOutboxStatus.PENDING,
                10
        );

        // then
        assertThat(found).extracting(ProblemEventOutbox::getId).contains(outboxId);
    }

    @Test
    @DisplayName("nextAttemptAt이 현재 시각보다 과거이면 polling 대상에 포함된다")
    void findPollableByStatus_nextAttemptAtBeforeNow_isIncluded() {
        // given
        ProblemEventOutbox outbox = createOutbox(Instant.parse("2026-09-21T00:00:00Z"));

        problemEventOutboxRepository.save(outbox);
        entityManager.flush();

        UUID outboxId = outbox.getId();

        setNextAttemptAtToPast(outboxId);

        entityManager.flush();
        entityManager.clear();

        // when
        List<ProblemEventOutbox> found = problemEventOutboxRepository.findPollableByStatus(
                ProblemEventOutboxStatus.PENDING,
                10
        );

        // then
        assertThat(found).extracting(ProblemEventOutbox::getId).contains(outboxId);
    }

    @Test
    @DisplayName("nextAttemptAt이 현재 시각과 같으면 polling 대상에 포함된다")
    void findPollableByStatus_nextAttemptAtEqualsNow_isIncluded() {
        // given
        ProblemEventOutbox outbox = createOutbox(Instant.parse("2026-09-21T00:00:00Z"));

        problemEventOutboxRepository.save(outbox);
        entityManager.flush();

        UUID outboxId = outbox.getId();

        setNextAttemptAtToCurrentDatabaseTime(outboxId);

        entityManager.flush();
        entityManager.clear();

        // when
        List<ProblemEventOutbox> found = problemEventOutboxRepository.findPollableByStatus(
                ProblemEventOutboxStatus.PENDING,
                10
        );

        // then
        assertThat(found).extracting(ProblemEventOutbox::getId).contains(outboxId);
    }

    @Test
    @DisplayName("nextAttemptAt이 현재 시각보다 미래이면 polling 대상에서 제외된다")
    void findPollableByStatus_nextAttemptAtAfterNow_isExcluded() {
        // given
        ProblemEventOutbox outbox = createOutbox(Instant.parse("2026-09-21T00:00:00Z"));

        problemEventOutboxRepository.save(outbox);
        entityManager.flush();

        UUID outboxId = outbox.getId();

        setNextAttemptAtToFuture(outboxId);

        entityManager.flush();
        entityManager.clear();

        // when
        List<ProblemEventOutbox> found = problemEventOutboxRepository.findPollableByStatus(
                ProblemEventOutboxStatus.PENDING,
                10
        );

        // then
        assertThat(found).extracting(ProblemEventOutbox::getId).doesNotContain(outboxId);
    }

    @Test
    @DisplayName("polling 조회는 nextAttemptAt이 null이거나 현재 시각 이하인 Outbox만 반환한다")
    void findPollableByStatus_filtersByNextAttemptAt() {
        // given
        Instant baseTime = Instant.parse("2026-09-21T00:00:00Z");

        ProblemEventOutbox nullNextAttemptAt = createOutbox(baseTime);
        ProblemEventOutbox due = createOutbox(baseTime.plusSeconds(1));
        ProblemEventOutbox future = createOutbox(baseTime.plusSeconds(2));

        problemEventOutboxRepository.save(nullNextAttemptAt);
        problemEventOutboxRepository.save(due);
        problemEventOutboxRepository.save(future);

        entityManager.flush();

        UUID nullNextAttemptAtId = nullNextAttemptAt.getId();
        UUID dueId = due.getId();
        UUID futureId = future.getId();

        setNextAttemptAtToPast(dueId);
        setNextAttemptAtToFuture(futureId);

        entityManager.flush();
        entityManager.clear();

        // when
        List<ProblemEventOutbox> found = problemEventOutboxRepository.findPollableByStatus(
                ProblemEventOutboxStatus.PENDING,
                10
        );

        // then
        assertThat(found).extracting(ProblemEventOutbox::getId)
                .containsExactly(nullNextAttemptAtId, dueId)
                .doesNotContain(futureId);
    }

    @Test
    @DisplayName("polling 조회는 occurredAt ASC, id ASC 순서로 안정적으로 정렬한다")
    void findPollableByStatus_ordersByOccurredAtAndId() {
        // given
        Instant baseTime = Instant.parse("2026-09-21T00:00:00Z");

        ProblemEventOutbox firstSameTime = createOutbox(baseTime);
        ProblemEventOutbox secondSameTime = createOutbox(baseTime);
        ProblemEventOutbox later = createOutbox(baseTime.plusSeconds(1));

        problemEventOutboxRepository.save(secondSameTime);
        problemEventOutboxRepository.save(later);
        problemEventOutboxRepository.save(firstSameTime);

        entityManager.flush();

        UUID firstSameTimeId = firstSameTime.getId();
        UUID secondSameTimeId = secondSameTime.getId();
        UUID laterId = later.getId();

        List<UUID> sameTimeIds = List.of(firstSameTimeId, secondSameTimeId).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        entityManager.clear();

        // when
        List<ProblemEventOutbox> found = problemEventOutboxRepository.findPollableByStatus(
                ProblemEventOutboxStatus.PENDING,
                10
        );

        // then
        assertThat(found).hasSize(3);
        assertThat(found).extracting(ProblemEventOutbox::getId)
                .containsExactly(sameTimeIds.get(0), sameTimeIds.get(1), laterId);
    }

    @Test
    @DisplayName("polling 조회는 지정한 batch limit만큼만 반환한다")
    void findPollableByStatus_appliesBatchLimit() {
        // given
        Instant baseTime = Instant.parse("2026-09-21T00:00:00Z");

        ProblemEventOutbox first = createOutbox(baseTime);
        ProblemEventOutbox second = createOutbox(baseTime.plusSeconds(1));
        ProblemEventOutbox third = createOutbox(baseTime.plusSeconds(2));

        problemEventOutboxRepository.save(third);
        problemEventOutboxRepository.save(first);
        problemEventOutboxRepository.save(second);

        entityManager.flush();

        UUID firstId = first.getId();
        UUID secondId = second.getId();
        UUID thirdId = third.getId();

        entityManager.clear();

        // when
        List<ProblemEventOutbox> found = problemEventOutboxRepository.findPollableByStatus(
                ProblemEventOutboxStatus.PENDING,
                2
        );

        // then
        assertThat(found).hasSize(2);
        assertThat(found).extracting(ProblemEventOutbox::getId)
                .containsExactly(firstId, secondId)
                .doesNotContain(thirdId);
    }

    @Test
    @DisplayName("새 ProblemEventOutbox를 저장하면 lockVersion은 0으로 시작한다")
    void save_newOutbox_initializesLockVersionToZero() {
        // given
        ProblemEventOutbox outbox = createOutbox(Instant.parse("2026-09-21T00:00:00Z"));

        // when
        ProblemEventOutbox saved = problemEventOutboxRepository.save(outbox);

        entityManager.flush();

        UUID outboxId = saved.getId();

        entityManager.clear();

        // then
        Long lockVersion = readLockVersion(outboxId);

        assertThat(lockVersion).isZero();
    }

    @Test
    @DisplayName("ProblemEventOutbox를 JPA로 수정하면 lockVersion이 1 증가한다")
    void updateOutbox_incrementsLockVersion() {
        // given
        Instant occurredAt = Instant.parse("2026-09-21T00:00:00Z");
        ProblemEventOutbox outbox = createOutbox(occurredAt);
        ProblemEventOutbox saved = problemEventOutboxRepository.save(outbox);

        entityManager.flush();

        UUID outboxId = saved.getId();

        assertThat(readLockVersion(outboxId)).isZero();

        entityManager.clear();

        ProblemEventOutbox found = problemEventOutboxRepository.findById(outboxId).orElseThrow();

        // when
        found.markPublished(occurredAt.plusSeconds(10));

        problemEventOutboxRepository.save(found);

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(readLockVersion(outboxId)).isEqualTo(1L);

        ProblemEventOutbox updated = problemEventOutboxRepository.findById(outboxId).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
        assertThat(updated.getPublishedAt()).isEqualTo(occurredAt.plusSeconds(10));
    }

    @Test
    @DisplayName("같은 Outbox row를 두 객체가 수정하면 늦게 저장한 객체에서 optimistic lock 충돌이 발생한다")
    void updateSameOutboxFromTwoDetachedEntities_throwsOptimisticLockException() {
        // given
        Instant occurredAt = Instant.parse("2026-09-21T00:00:00Z");
        ProblemEventOutbox outbox = createOutbox(occurredAt);
        ProblemEventOutbox saved = problemEventOutboxRepository.save(outbox);

        entityManager.flush();

        UUID outboxId = saved.getId();

        entityManager.clear();

        ProblemEventOutbox first = problemEventOutboxRepository.findById(outboxId).orElseThrow();

        entityManager.detach(first);

        ProblemEventOutbox second = problemEventOutboxRepository.findById(outboxId).orElseThrow();

        entityManager.detach(second);

        assertThat(readLockVersion(outboxId)).isZero();

        first.markPublished(occurredAt.plusSeconds(10));

        problemEventOutboxRepository.save(first);

        entityManager.flush();
        entityManager.clear();

        assertThat(readLockVersion(outboxId)).isEqualTo(1L);

        second.markPublished(occurredAt.plusSeconds(20));

        // when & then
        assertThatThrownBy(() -> {
            problemEventOutboxRepository.save(second);
            entityManager.flush();
        }).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private ProblemEventOutbox createOutbox(Instant occurredAt) {
        return createOutbox(UUID.randomUUID(), UUID.randomUUID(), occurredAt);
    }

    private ProblemEventOutbox createOutbox(UUID eventId, UUID problemId, Instant occurredAt) {
        UUID problemVersionId = UUID.randomUUID();

        return ProblemEventOutbox.createPending(
                eventId,
                problemId,
                1,
                createPayload(eventId, problemId, problemVersionId),
                occurredAt
        );
    }

    private void setStatus(UUID outboxId, ProblemEventOutboxStatus status) {
        entityManager.createNativeQuery(
                        """
                        UPDATE content_schema.p_problem_event_outboxes
                        SET status = :status
                        WHERE id = :outboxId
                        """
                ).setParameter("status", status.name())
                .setParameter("outboxId", outboxId)
                .executeUpdate();
    }

    private void setNextAttemptAtToPast(UUID outboxId) {
        entityManager.createNativeQuery(
                """
                UPDATE content_schema.p_problem_event_outboxes
                SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE id = :outboxId
                """
        ).setParameter("outboxId", outboxId).executeUpdate();
    }

    private void setNextAttemptAtToCurrentDatabaseTime(UUID outboxId) {
        entityManager.createNativeQuery(
                """
                UPDATE content_schema.p_problem_event_outboxes
                SET next_attempt_at = CURRENT_TIMESTAMP
                WHERE id = :outboxId
                """
        ).setParameter("outboxId", outboxId).executeUpdate();
    }

    private void setNextAttemptAtToFuture(UUID outboxId) {
        entityManager.createNativeQuery(
                """
                UPDATE content_schema.p_problem_event_outboxes
                SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 hour'
                WHERE id = :outboxId
                """
        ).setParameter("outboxId", outboxId).executeUpdate();
    }

    private Instant readNextAttemptAt(UUID outboxId) {
        Object result = entityManager.createNativeQuery(
                """
                SELECT next_attempt_at
                FROM content_schema.p_problem_event_outboxes
                WHERE id = :outboxId
                """
        ).setParameter("outboxId", outboxId).getSingleResult();

        if (result == null) {
            return null;
        }

        return ((java.time.OffsetDateTime) result).toInstant();
    }

    private Long readLockVersion(UUID outboxId) {
        Number result = (Number) entityManager.createNativeQuery(
                """
                SELECT lock_version
                FROM content_schema.p_problem_event_outboxes
                WHERE id = :outboxId
                """
        ).setParameter("outboxId", outboxId).getSingleResult();

        return result.longValue();
    }

    private String createPayload(UUID eventId, UUID problemId, UUID problemVersionId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "PROBLEM_PUBLISHED",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-21T00:00:00Z",
                  "problemId": "%s",
                  "problemVersionId": "%s",
                  "versionNo": 1,
                  "language": "JAVA",
                  "starterCode": "class Solution {}",
                  "testCases": [],
                  "timeLimit": 1000,
                  "memoryLimit": 128,
                  "publishedAt": "2026-09-21T00:00:00Z"
                }
                """.formatted(eventId, problemId, problemVersionId);
    }
}