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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.hibernate.exception.ConstraintViolationException;

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
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(
        FlywayAutoConfiguration.class
)
@Import({
        JpaAuditingConfig.class,
        ProblemEventOutboxRepositoryImpl.class
})
@EnableJpaRepositories(
        basePackageClasses = {
                SpringDataProblemEventOutboxRepository.class
        }
)
class ProblemEventOutboxRepositoryTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

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

        Instant occurredAt =
                Instant.parse(
                        "2026-09-21T00:00:00Z"
                );

        String payload =
                createPayload(
                        eventId,
                        problemId,
                        problemVersionId
                );

        ProblemEventOutbox outbox =
                ProblemEventOutbox.createPending(
                        eventId,
                        problemId,
                        1,
                        payload,
                        occurredAt
                );

        // when
        ProblemEventOutbox saved =
                problemEventOutboxRepository.save(
                        outbox
                );

        entityManager.flush();

        UUID outboxId =
                saved.getId();

        entityManager.clear();

        ProblemEventOutbox found =
                problemEventOutboxRepository
                        .findById(
                                outboxId
                        )
                        .orElseThrow();

        // then
        assertThat(found.getId())
                .isEqualTo(
                        outboxId
                );

        assertThat(found.getEventId())
                .isEqualTo(
                        eventId
                );

        assertThat(found.getAggregateType())
                .isEqualTo(
                        "PROBLEM"
                );

        assertThat(found.getAggregateId())
                .isEqualTo(
                        problemId
                );

        assertThat(found.getEventType())
                .isEqualTo(
                        "PROBLEM_PUBLISHED"
                );

        assertThat(found.getEventVersion())
                .isEqualTo(
                        1
                );

        assertThat(found.getStatus())
                .isEqualTo(
                        ProblemEventOutboxStatus.PENDING
                );

        assertThat(found.getRetryCount())
                .isZero();

        assertThat(found.getOccurredAt())
                .isEqualTo(
                        occurredAt
                );

        assertThat(found.getPublishedAt())
                .isNull();

        assertThat(found.getLastError())
                .isNull();

        assertThat(found.getPayload())
                .contains(
                        problemVersionId.toString()
                );

        String columnType =
                (String) entityManager
                        .createNativeQuery(
                                """
                                SELECT pg_typeof(payload)::text
                                FROM content_schema.p_problem_event_outboxes
                                WHERE id = :outboxId
                                """
                        )
                        .setParameter(
                                "outboxId",
                                outboxId
                        )
                        .getSingleResult();

        assertThat(columnType)
                .isEqualTo(
                        "jsonb"
                );

        String storedProblemVersionId =
                (String) entityManager
                        .createNativeQuery(
                                """
                                SELECT payload ->> 'problemVersionId'
                                FROM content_schema.p_problem_event_outboxes
                                WHERE id = :outboxId
                                """
                        )
                        .setParameter(
                                "outboxId",
                                outboxId
                        )
                        .getSingleResult();

        assertThat(storedProblemVersionId)
                .isEqualTo(
                        problemVersionId.toString()
                );
    }

    @Test
    @DisplayName("같은 eventId의 ProblemEventOutbox를 두 번 저장하면 DB UNIQUE 제약으로 차단된다")
    void saveDuplicateEventId_throwsException() {
        // given
        UUID eventId = UUID.randomUUID();

        ProblemEventOutbox firstOutbox =
                createOutbox(
                        eventId,
                        UUID.randomUUID(),
                        Instant.parse("2026-09-21T00:00:00Z")
                );

        problemEventOutboxRepository.save(firstOutbox);

        entityManager.flush();
        entityManager.clear();

        ProblemEventOutbox secondOutbox =
                createOutbox(
                        eventId,
                        UUID.randomUUID(),
                        Instant.parse("2026-09-21T00:00:01Z")
                );

        // when & then
        assertThatThrownBy(
                () -> {
                    problemEventOutboxRepository.save(secondOutbox);
                    entityManager.flush();
                }
        )
                .isInstanceOf(
                        ConstraintViolationException.class
                )
                .hasMessageContaining(
                        "uk_problem_event_outboxes_event_id"
                );
    }

    @Test
    @DisplayName("PENDING Outbox를 발생 시각과 ID 오름차순으로 안정적으로 조회한다")
    void findPendingBatch_ordersByOccurredAtAndId() {
        // given
        Instant baseTime =
                Instant.parse(
                        "2026-09-21T00:00:00Z"
                );

        ProblemEventOutbox firstSameTime =
                createOutbox(
                        baseTime
                );

        ProblemEventOutbox secondSameTime =
                createOutbox(
                        baseTime
                );

        ProblemEventOutbox later =
                createOutbox(
                        baseTime.plusSeconds(1)
                );

        problemEventOutboxRepository.save(
                secondSameTime
        );

        problemEventOutboxRepository.save(
                later
        );

        problemEventOutboxRepository.save(
                firstSameTime
        );

        entityManager.flush();

        List<UUID> sameTimeIds =
                List.of(
                                firstSameTime.getId(),
                                secondSameTime.getId()
                        )
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        UUID::toString
                                )
                        )
                        .toList();

        entityManager.clear();

        // when
        List<ProblemEventOutbox> found =
                problemEventOutboxRepository
                        .findAllByStatusOrderByOccurredAtAscIdAsc(
                                ProblemEventOutboxStatus.PENDING,
                                10
                        );

        // then
        assertThat(found)
                .hasSize(3);

        assertThat(found)
                .extracting(
                        ProblemEventOutbox::getId
                )
                .containsExactly(
                        sameTimeIds.get(0),
                        sameTimeIds.get(1),
                        later.getId()
                );

        assertThat(found)
                .extracting(
                        ProblemEventOutbox::getStatus
                )
                .containsOnly(
                        ProblemEventOutboxStatus.PENDING
                );
    }

    @Test
    @DisplayName("PUBLISHED Outbox는 PENDING 배치 조회에서 제외된다")
    void findPendingBatch_excludesPublishedOutbox() {
        // given
        Instant baseTime =
                Instant.parse(
                        "2026-09-21T00:00:00Z"
                );

        ProblemEventOutbox pending =
                createOutbox(
                        baseTime.plusSeconds(1)
                );

        ProblemEventOutbox published =
                createOutbox(
                        baseTime
                );

        published.markPublished(
                baseTime.plusSeconds(10)
        );

        problemEventOutboxRepository.save(
                published
        );

        problemEventOutboxRepository.save(
                pending
        );

        entityManager.flush();

        UUID pendingId =
                pending.getId();

        UUID publishedId =
                published.getId();

        entityManager.clear();

        // when
        List<ProblemEventOutbox> found =
                problemEventOutboxRepository
                        .findAllByStatusOrderByOccurredAtAscIdAsc(
                                ProblemEventOutboxStatus.PENDING,
                                10
                        );

        // then
        assertThat(found)
                .extracting(
                        ProblemEventOutbox::getId
                )
                .containsExactly(
                        pendingId
                )
                .doesNotContain(
                        publishedId
                );
    }

    @Test
    @DisplayName("PENDING Outbox 조회는 지정한 배치 크기를 적용한다")
    void findPendingBatch_appliesLimit() {
        // given
        Instant baseTime =
                Instant.parse(
                        "2026-09-21T00:00:00Z"
                );

        ProblemEventOutbox first =
                createOutbox(
                        baseTime
                );

        ProblemEventOutbox second =
                createOutbox(
                        baseTime.plusSeconds(1)
                );

        ProblemEventOutbox third =
                createOutbox(
                        baseTime.plusSeconds(2)
                );

        problemEventOutboxRepository.save(
                third
        );

        problemEventOutboxRepository.save(
                first
        );

        problemEventOutboxRepository.save(
                second
        );

        entityManager.flush();

        UUID firstId =
                first.getId();

        UUID secondId =
                second.getId();

        entityManager.clear();

        // when
        List<ProblemEventOutbox> found =
                problemEventOutboxRepository
                        .findAllByStatusOrderByOccurredAtAscIdAsc(
                                ProblemEventOutboxStatus.PENDING,
                                2
                        );

        // then
        assertThat(found)
                .hasSize(2);

        assertThat(found)
                .extracting(
                        ProblemEventOutbox::getId
                )
                .containsExactly(
                        firstId,
                        secondId
                );
    }

    private ProblemEventOutbox createOutbox(
            Instant occurredAt
    ) {
        return createOutbox(
                UUID.randomUUID(),
                UUID.randomUUID(),
                occurredAt
        );
    }

    private ProblemEventOutbox createOutbox(
            UUID eventId,
            UUID problemId,
            Instant occurredAt
    ) {
        UUID problemVersionId =
                UUID.randomUUID();

        return ProblemEventOutbox.createPending(
                eventId,
                problemId,
                1,
                createPayload(
                        eventId,
                        problemId,
                        problemVersionId
                ),
                occurredAt
        );
    }

    private String createPayload(
            UUID eventId,
            UUID problemId,
            UUID problemVersionId
    ) {
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
                """.formatted(
                eventId,
                problemId,
                problemVersionId
        );
    }
}