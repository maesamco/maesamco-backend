package com.maesamco.content.problem.infrastructure.persistence;

import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import com.maesamco.content.problem.domain.repository.ProblemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Problem Event Outbox의 PostgreSQL 저장 규칙을 검증합니다.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=content_schema",
        "spring.data.jpa.repositories.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@EnableJpaRepositories(
        basePackageClasses = ProblemEventOutboxRepository.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = ProblemRepository.class
        )
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
    @DisplayName(
            "ProblemEventOutbox를 저장하면 "
                    + "PostgreSQL JSONB로 저장되고 다시 조회된다"
    )
    void saveAndFind_restoresJsonbPayload() {
        UUID eventId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();

        Instant occurredAt =
                Instant.parse(
                        "2026-09-08T00:00:00Z"
                );

        String payload =
                """
                {
                  "eventId": "%s",
                  "eventType": "PROBLEM_PUBLISHED",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-08T00:00:00Z",
                  "problemId": "%s",
                  "problemVersionId": "%s",
                  "versionNo": 1,
                  "language": "JAVA",
                  "starterCode": "class Solution {}",
                  "testCases": [],
                  "timeLimit": 1000,
                  "memoryLimit": 128,
                  "publishedAt": "2026-09-08T00:00:00Z"
                }
                """.formatted(
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

        ProblemEventOutbox saved =
                problemEventOutboxRepository.saveAndFlush(
                        outbox
                );

        UUID outboxId = saved.getId();

        entityManager.clear();

        ProblemEventOutbox found =
                problemEventOutboxRepository.findById(
                        outboxId
                ).orElseThrow();

        assertThat(found.getId())
                .isEqualTo(outboxId);

        assertThat(found.getEventId())
                .isEqualTo(eventId);

        assertThat(found.getAggregateType())
                .isEqualTo("PROBLEM");

        assertThat(found.getAggregateId())
                .isEqualTo(problemId);

        assertThat(found.getEventType())
                .isEqualTo("PROBLEM_PUBLISHED");

        assertThat(found.getEventVersion())
                .isEqualTo(1);

        assertThat(found.getStatus())
                .isEqualTo(
                        ProblemEventOutboxStatus.PENDING
                );

        assertThat(found.getRetryCount())
                .isZero();

        assertThat(found.getOccurredAt())
                .isEqualTo(occurredAt);

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
                .isEqualTo("jsonb");

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
    @DisplayName(
            "같은 ProblemVersion의 ProblemPublished Outbox를 "
                    + "두 번 저장하면 DB UNIQUE 제약으로 차단된다"
    )
    void saveDuplicateProblemVersion_throwsException() {
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();

        Instant occurredAt =
                Instant.parse(
                        "2026-09-08T00:00:00Z"
                );

        String firstPayload =
                createPayload(
                        UUID.randomUUID(),
                        problemId,
                        problemVersionId
                );

        ProblemEventOutbox firstOutbox =
                ProblemEventOutbox.createPending(
                        UUID.randomUUID(),
                        problemId,
                        1,
                        firstPayload,
                        occurredAt
                );

        problemEventOutboxRepository.saveAndFlush(
                firstOutbox
        );

        String secondPayload =
                createPayload(
                        UUID.randomUUID(),
                        problemId,
                        problemVersionId
                );

        ProblemEventOutbox secondOutbox =
                ProblemEventOutbox.createPending(
                        UUID.randomUUID(),
                        problemId,
                        1,
                        secondPayload,
                        occurredAt.plusSeconds(1)
                );

        assertThatThrownBy(
                () ->
                        problemEventOutboxRepository.saveAndFlush(
                                secondOutbox
                        )
        )
                .isInstanceOf(
                        DataIntegrityViolationException.class
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
                  "occurredAt": "2026-09-08T00:00:00Z",
                  "problemId": "%s",
                  "problemVersionId": "%s",
                  "versionNo": 1,
                  "language": "JAVA",
                  "starterCode": "class Solution {}",
                  "testCases": [],
                  "timeLimit": 1000,
                  "memoryLimit": 128,
                  "publishedAt": "2026-09-08T00:00:00Z"
                }
                """.formatted(
                eventId,
                problemId,
                problemVersionId
        );
    }
}
