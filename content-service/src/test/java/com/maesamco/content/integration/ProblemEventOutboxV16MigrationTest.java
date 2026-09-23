package com.maesamco.content.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
@DisplayName("V16 ProblemEventOutbox Migration 통합 테스트")
class ProblemEventOutboxV16MigrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Test
    @DisplayName(
            "V16 마이그레이션은 기존 Outbox 데이터를 유지하고 "
                    + "재시도 백오프 및 낙관적 락 컬럼과 polling 인덱스를 추가한다"
    )
    void migrateV16_withLegacyProblemEventOutbox_succeedsAndPreservesData()
            throws SQLException {

        // given
        migrateTo("15");

        UUID outboxId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        insertLegacyProblemEventOutbox(
                outboxId,
                eventId,
                aggregateId
        );

        assertThat(countProblemEventOutboxes()).isEqualTo(1);

        // when
        Flyway flywayToV16 = flyway("16");

        assertThatCode(flywayToV16::migrate)
                .doesNotThrowAnyException();

        // then
        assertThat(countProblemEventOutboxes()).isEqualTo(1);

        ProblemEventOutboxSnapshot snapshot =
                findProblemEventOutbox(outboxId);

        assertThat(snapshot.id())
                .isEqualTo(outboxId);

        assertThat(snapshot.eventId())
                .isEqualTo(eventId);

        assertThat(snapshot.status())
                .isEqualTo("PENDING");

        assertThat(snapshot.retryCount())
                .isZero();

        assertThat(snapshot.lockVersion())
                .isZero();

        assertThat(snapshot.nextAttemptAt())
                .isNull();

        assertThat(isColumnNullable("lock_version"))
                .isFalse();

        assertThat(columnDefault("lock_version"))
                .contains("0");

        assertThat(isColumnNullable("next_attempt_at"))
                .isTrue();

        assertThat(hasIndex(
                "idx_problem_event_outboxes_pollable"
        )).isTrue();

        assertThat(indexDefinition(
                "idx_problem_event_outboxes_pollable"
        ))
                .contains(
                        "status",
                        "next_attempt_at",
                        "occurred_at",
                        "id"
                );

        assertThat(hasIndex(
                "idx_problem_event_outboxes_status"
        )).isTrue();

        assertThat(hasIndex(
                "idx_problem_event_outboxes_status_occurred_at_id"
        )).isTrue();
    }

    private void migrateTo(String version) {
        flyway(version).migrate();
    }

    private Flyway flyway(String version) {
        return Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .schemas("content_schema")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    private void insertLegacyProblemEventOutbox(
            UUID outboxId,
            UUID eventId,
            UUID aggregateId
    ) throws SQLException {

        String sql = """
                INSERT INTO content_schema.p_problem_event_outboxes
                (
                    id,
                    event_id,
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    event_version,
                    payload,
                    status,
                    retry_count,
                    occurred_at,
                    published_at,
                    last_error
                )
                VALUES
                (
                    ?,
                    ?,
                    'PROBLEM',
                    ?,
                    'PROBLEM_PUBLISHED',
                    1,
                    CAST(? AS JSONB),
                    'PENDING',
                    0,
                    NOW(),
                    NULL,
                    NULL
                )
                """;

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setObject(1, outboxId);
            statement.setObject(2, eventId);
            statement.setObject(3, aggregateId);
            statement.setString(
                    4,
                    """
                    {
                      "problemId": "%s"
                    }
                    """.formatted(aggregateId)
            );

            statement.executeUpdate();
        }
    }

    private long countProblemEventOutboxes()
            throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM content_schema.p_problem_event_outboxes
                """;

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet resultSet =
                        statement.executeQuery()
        ) {
            resultSet.next();

            return resultSet.getLong(1);
        }
    }

    private ProblemEventOutboxSnapshot findProblemEventOutbox(
            UUID outboxId
    ) throws SQLException {

        String sql = """
                SELECT
                    id,
                    event_id,
                    status,
                    retry_count,
                    lock_version,
                    next_attempt_at
                FROM content_schema.p_problem_event_outboxes
                WHERE id = ?
                """;

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setObject(1, outboxId);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();

                return new ProblemEventOutboxSnapshot(
                        resultSet.getObject(
                                "id",
                                UUID.class
                        ),
                        resultSet.getObject(
                                "event_id",
                                UUID.class
                        ),
                        resultSet.getString("status"),
                        resultSet.getInt("retry_count"),
                        resultSet.getLong("lock_version"),
                        resultSet.getObject("next_attempt_at")
                );
            }
        }
    }

    private boolean isColumnNullable(
            String columnName
    ) throws SQLException {

        String sql = """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'content_schema'
                  AND table_name = 'p_problem_event_outboxes'
                  AND column_name = ?
                """;

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, columnName);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();

                return "YES".equals(
                        resultSet.getString("is_nullable")
                );
            }
        }
    }

    private String columnDefault(
            String columnName
    ) throws SQLException {

        String sql = """
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = 'content_schema'
                  AND table_name = 'p_problem_event_outboxes'
                  AND column_name = ?
                """;

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, columnName);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();

                return resultSet.getString("column_default");
            }
        }
    }

    private boolean hasIndex(
            String indexName
    ) throws SQLException {

        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_indexes
                    WHERE schemaname = 'content_schema'
                      AND tablename = 'p_problem_event_outboxes'
                      AND indexname = ?
                )
                """;

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, indexName);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();

                return resultSet.getBoolean(1);
            }
        }
    }

    private String indexDefinition(
            String indexName
    ) throws SQLException {

        String sql = """
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'content_schema'
                  AND tablename = 'p_problem_event_outboxes'
                  AND indexname = ?
                """;

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, indexName);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();

                return resultSet.getString("indexdef");
            }
        }
    }

    private record ProblemEventOutboxSnapshot(
            UUID id,
            UUID eventId,
            String status,
            int retryCount,
            long lockVersion,
            Object nextAttemptAt
    ) {
    }
}