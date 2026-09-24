package com.maesamco.content.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("V18 ProblemEventOutbox claim/lease Migration 통합 테스트 (#160)")
class ProblemEventOutboxV18MigrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Test
    @DisplayName("V18 마이그레이션은 기존 Outbox 데이터를 유지하고 선점 컬럼, 상태/선점 CHECK 제약, lease 인덱스를 추가한다")
    void migrateV18_preservesDataAndAddsClaimColumns() throws SQLException {
        // given
        flyway("17").migrate();

        UUID pendingId = UUID.randomUUID();
        UUID publishedId = UUID.randomUUID();

        insertOutbox(pendingId, "PENDING", null);
        insertOutbox(publishedId, "PUBLISHED", "NOW()");

        // when
        assertThatCode(() -> flyway("18").migrate()).doesNotThrowAnyException();

        // then: 기존 데이터는 선점 정보 없이 그대로 유지됩니다.
        assertThat(queryString("SELECT status FROM content_schema.p_problem_event_outboxes WHERE id = '" + pendingId + "'"))
                .isEqualTo("PENDING");
        assertThat(queryString("SELECT status FROM content_schema.p_problem_event_outboxes WHERE id = '" + publishedId + "'"))
                .isEqualTo("PUBLISHED");
        assertThat(queryString(
                "SELECT COUNT(*)::text FROM content_schema.p_problem_event_outboxes "
                        + "WHERE claim_id IS NOT NULL OR lease_until IS NOT NULL"
        )).isEqualTo("0");

        assertThat(queryString(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'content_schema' "
                        + "AND indexname = 'idx_problem_event_outboxes_in_progress_lease'"
        )).contains("lease_until").contains("IN_PROGRESS");

        // IN_PROGRESS 전이는 claim_id + lease_until 과 함께일 때만 허용됩니다.
        assertThatCode(() -> execute(
                "UPDATE content_schema.p_problem_event_outboxes "
                        + "SET status = 'IN_PROGRESS', claim_id = gen_random_uuid(), "
                        + "lease_until = NOW() + INTERVAL '1 minute' "
                        + "WHERE id = '" + pendingId + "'"
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> execute(
                "UPDATE content_schema.p_problem_event_outboxes "
                        + "SET status = 'PENDING' WHERE id = '" + pendingId + "'"
        )).hasMessageContaining("chk_problem_event_outboxes_claim");

        assertThatThrownBy(() -> execute(
                "UPDATE content_schema.p_problem_event_outboxes "
                        + "SET claim_id = NULL WHERE id = '" + pendingId + "'"
        )).hasMessageContaining("chk_problem_event_outboxes_claim");
    }

    private Flyway flyway(String version) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("content_schema")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    private void insertOutbox(UUID outboxId, String status, String publishedAtSql) throws SQLException {
        String sql = """
                INSERT INTO content_schema.p_problem_event_outboxes
                (id, event_id, aggregate_type, aggregate_id, event_type, event_version,
                 payload, status, retry_count, occurred_at, published_at, last_error)
                VALUES (?, ?, 'PROBLEM', ?, 'PROBLEM_PUBLISHED', 1,
                        CAST(? AS JSONB), ?, 0, NOW(), %s, NULL)
                """.formatted(publishedAtSql == null ? "NULL" : publishedAtSql);

        UUID aggregateId = UUID.randomUUID();

        try (
                Connection connection = postgres.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setObject(1, outboxId);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, aggregateId);
            statement.setString(4, "{\"problemId\": \"%s\"}".formatted(aggregateId));
            statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    private void execute(String sql) throws SQLException {
        try (
                Connection connection = postgres.createConnection("");
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(sql);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (
                Connection connection = postgres.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
