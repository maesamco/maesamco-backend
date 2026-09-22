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
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
@DisplayName("V15 ProblemProgress Migration 통합 테스트")
class ProblemProgressV15MigrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    @Test
    @DisplayName(
            "기존 ProblemProgress 데이터가 존재해도 "
                    + "V15 마이그레이션이 성공하고 기존 데이터는 초기화된다"
    )
    void migrateV15_withLegacyProblemProgress_succeedsAndClearsLegacyData()
            throws SQLException {

        // given
        Flyway flywayToV14 =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword()
                        )
                        .schemas("content_schema")
                        .locations("classpath:db/migration")
                        .target(MigrationVersion.fromVersion("14"))
                        .load();

        flywayToV14.migrate();

        UUID problemId = UUID.randomUUID();

        insertLegacyProblem(problemId);
        insertLegacyProblemProgress(problemId);

        assertThat(countProblemProgress())
                .isEqualTo(1);

        assertThat(countProblem(problemId))
                .isEqualTo(1);

        Flyway flywayToV15 =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword()
                        )
                        .schemas("content_schema")
                        .locations("classpath:db/migration")
                        .target(MigrationVersion.fromVersion("15"))
                        .load();

        // when & then
        assertThatCode(
                () -> flywayToV15.migrate()
        ).doesNotThrowAnyException();

        assertThat(countProblemProgress())
                .isZero();

        assertThat(countProblem(problemId))
                .isEqualTo(1);

        assertThat(isColumnNullable("created_at"))
                .isFalse();

        assertThat(isColumnNullable("attempt_no"))
                .isFalse();

        assertThat(isColumnNullable("lock_version"))
                .isFalse();

        assertThat(
                hasCheckConstraint(
                        "ck_p_problem_progress_status"
                )
        ).isTrue();

        assertThat(
                hasCheckConstraint(
                        "ck_p_problem_progress_attempt_no"
                )
        ).isTrue();
    }

    private void insertLegacyProblem(
            UUID problemId
    ) throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO content_schema.p_problems (
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
                                    created_by,
                                    updated_by
                                )
                                VALUES (
                                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                                )
                                """
                        )
        ) {
            statement.setObject(
                    1,
                    problemId
            );

            statement.setString(
                    2,
                    "legacy problem"
            );

            statement.setString(
                    3,
                    "legacy problem description"
            );

            statement.setString(
                    4,
                    "CODE"
            );

            statement.setString(
                    5,
                    "DEFAULT"
            );

            statement.setString(
                    6,
                    "EASY"
            );

            statement.setString(
                    7,
                    "HUMAN_AUTHORED"
            );

            statement.setString(
                    8,
                    "PUBLISHED"
            );

            statement.setString(
                    9,
                    "class Solution {}"
            );

            statement.setString(
                    10,
                    "SECOND_1"
            );

            statement.setString(
                    11,
                    "MB_128"
            );

            statement.setString(
                    12,
                    "JAVA"
            );

            statement.setInt(
                    13,
                    1
            );

            statement.setObject(
                    14,
                    UUID.randomUUID()
            );

            statement.setObject(
                    15,
                    UUID.randomUUID()
            );

            statement.executeUpdate();
        }
    }

    private void insertLegacyProblemProgress(
            UUID problemId
    ) throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO content_schema.p_problem_progress (
                                    id,
                                    user_id,
                                    problem_id,
                                    version_no,
                                    solved_at,
                                    progress_status
                                )
                                VALUES (?, ?, ?, ?, NULL, ?)
                                """
                        )
        ) {
            statement.setObject(
                    1,
                    UUID.randomUUID()
            );

            statement.setObject(
                    2,
                    UUID.randomUUID()
            );

            statement.setObject(
                    3,
                    problemId
            );

            statement.setInt(
                    4,
                    1
            );

            statement.setString(
                    5,
                    "WRONG"
            );

            statement.executeUpdate();
        }
    }

    private int countProblemProgress()
            throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT COUNT(*)
                                FROM content_schema.p_problem_progress
                                """
                        );
                ResultSet resultSet =
                        statement.executeQuery()
        ) {
            resultSet.next();

            return resultSet.getInt(1);
        }
    }

    private int countProblem(
            UUID problemId
    ) throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT COUNT(*)
                                FROM content_schema.p_problems
                                WHERE id = ?
                                """
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
                resultSet.next();

                return resultSet.getInt(1);
            }
        }
    }

    private boolean isColumnNullable(
            String columnName
    ) throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT is_nullable
                                FROM information_schema.columns
                                WHERE table_schema = 'content_schema'
                                  AND table_name = 'p_problem_progress'
                                  AND column_name = ?
                                """
                        )
        ) {
            statement.setString(
                    1,
                    columnName
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                assertThat(resultSet.next())
                        .isTrue();

                return "YES".equals(
                        resultSet.getString(
                                "is_nullable"
                        )
                );
            }
        }
    }

    private boolean hasCheckConstraint(
            String constraintName
    ) throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM information_schema.table_constraints
                                    WHERE constraint_schema = 'content_schema'
                                      AND table_name = 'p_problem_progress'
                                      AND constraint_name = ?
                                      AND constraint_type = 'CHECK'
                                )
                                """
                        )
        ) {
            statement.setString(
                    1,
                    constraintName
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                resultSet.next();

                return resultSet.getBoolean(1);
            }
        }
    }
}