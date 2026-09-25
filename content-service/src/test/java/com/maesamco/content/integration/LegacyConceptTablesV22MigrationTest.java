package com.maesamco.content.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("V22 옛 개념 테이블 삭제 마이그레이션 통합 테스트")
class LegacyConceptTablesV22MigrationTest {

    private static final String[] LEGACY_TABLES = {"p_lesson_concepts", "p_problem_concepts", "p_concepts"};

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Test
    @DisplayName("비어 있는 옛 개념 테이블 3개를 삭제하고 태그 테이블은 그대로 둔다")
    void migrateV22_dropsEmptyLegacyTablesAndKeepsTagTables() throws Exception {
        // given: 테스트 순서와 무관하게 빈 스키마에서 시작해 V21까지 적용하면 V1 baseline이 만든 옛 테이블이 남아 있다.
        execute("DROP SCHEMA IF EXISTS content_schema CASCADE");
        migrateTo("21");
        for (String table : LEGACY_TABLES) {
            assertThat(tableExists(table)).as(table + " (V21)").isTrue();
        }

        // when
        migrateTo("22");

        // then
        for (String table : LEGACY_TABLES) {
            assertThat(tableExists(table)).as(table + " (V22)").isFalse();
        }
        assertThat(tableExists("p_tags")).isTrue();
        assertThat(tableExists("p_problem_tags")).isTrue();
    }

    @Test
    @DisplayName("옛 개념 테이블에 데이터가 남아 있으면 조용히 지우지 않고 마이그레이션이 실패한다")
    void migrateV22_failsWhenLegacyTableHasRows() throws Exception {
        // given: 이 테스트 전용으로 스키마를 비우고 V21까지 적용한다.
        execute("DROP SCHEMA IF EXISTS content_schema CASCADE");
        migrateTo("21");
        execute("""
                INSERT INTO content_schema.p_concepts
                    (name, created_at, created_by, updated_at, updated_by)
                VALUES ('legacy', NOW(), gen_random_uuid(), NOW(), gen_random_uuid())
                """);

        // when & then
        assertThatThrownBy(() -> migrateTo("22"))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("p_concepts");
        assertThat(tableExists("p_concepts")).isTrue();
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("content_schema")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                     "SELECT to_regclass('content_schema." + table + "') IS NOT NULL")) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
