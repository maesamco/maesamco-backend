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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("V19 ProblemType 명칭 마이그레이션 통합 테스트")
class ProblemTypeV19MigrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Test
    @DisplayName(
            "legacy MULTIPLE_CHOICE 문제 유형을 MULTI_SELECT로 변경한다"
    )
    void migrateV19_renamesLegacyMultipleChoice()
            throws Exception {

        // given
        migrateTo("18");

        UUID problemId =
                UUID.randomUUID();

        insertArchivedMultipleChoice(
                problemId
        );

        assertThat(
                findProblemType(problemId)
        ).isEqualTo(
                "MULTIPLE_CHOICE"
        );

        // when
        migrateTo("19");

        // then
        assertThat(
                findProblemType(problemId)
        ).isEqualTo(
                "MULTI_SELECT"
        );

        assertThat(
                findProblemStatus(problemId)
        ).isEqualTo(
                "ARCHIVED"
        );
    }

    private void migrateTo(
            String version
    ) {
        Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .schemas(
                        "content_schema"
                )
                .locations(
                        "classpath:db/migration"
                )
                .target(
                        MigrationVersion.fromVersion(
                                version
                        )
                )
                .load()
                .migrate();
    }

    private void insertArchivedMultipleChoice(
            UUID problemId
    ) throws SQLException {

        String sql = """
                INSERT INTO content_schema.p_problems
                (
                    id,
                    title,
                    description,
                    type,
                    timer_policy,
                    difficulty,
                    source,
                    problem_status,
                    starter_code,
                    running_time_limit,
                    running_memory_limit,
                    language,
                    current_version_no,
                    lock_version,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by
                )
                VALUES
                (
                    ?,
                    'legacy multiple choice',
                    'legacy problem',
                    'MULTIPLE_CHOICE',
                    'DEFAULT',
                    'EASY',
                    'HUMAN_AUTHORED',
                    'ARCHIVED',
                    NULL,
                    'SECOND_1',
                    'MB_128',
                    'JAVA',
                    1,
                    0,
                    NOW(),
                    ?,
                    NOW(),
                    ?
                )
                """;

        UUID actorId =
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000000"
                );

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            statement.setObject(
                    1,
                    problemId
            );

            statement.setObject(
                    2,
                    actorId
            );

            statement.setObject(
                    3,
                    actorId
            );

            statement.executeUpdate();
        }
    }

    private String findProblemType(
            UUID problemId
    ) throws SQLException {

        return findString(
                problemId,
                "type"
        );
    }

    private String findProblemStatus(
            UUID problemId
    ) throws SQLException {

        return findString(
                problemId,
                "problem_status"
        );
    }

    private String findString(
            UUID problemId,
            String column
    ) throws SQLException {

        String sql =
                "SELECT "
                        + column
                        + " FROM content_schema.p_problems "
                        + "WHERE id = ?";

        try (
                Connection connection =
                        postgres.createConnection("");

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            statement.setObject(
                    1,
                    problemId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                assertThat(
                        resultSet.next()
                ).isTrue();

                return resultSet.getString(
                        column
                );
            }
        }
    }
}
