package com.maesamco.content.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("V15 ProblemProgress Migration 통합 테스트")
class ProblemProgressV15MigrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    @BeforeEach
    void resetSchema() throws SQLException {
        try (
                Connection connection =
                        postgres.createConnection("");
                Statement statement =
                        connection.createStatement()
        ) {
            statement.execute(
                    "DROP SCHEMA IF EXISTS content_schema CASCADE"
            );

            statement.execute(
                    "CREATE SCHEMA content_schema"
            );
        }
    }

    @Test
    @DisplayName(
            "기존 WRONG/CORRECT ProblemProgress는 유지되고 "
                    + "신규 컬럼은 V15 정책에 맞게 초기화된다"
    )
    void migrateV15_withLegacyProgress_preservesAndBackfillsData()
            throws SQLException {

        // given
        migrateTo("14");

        UUID problemId = UUID.randomUUID();

        insertLegacyProblem(problemId);

        UUID wrongProgressId =
                insertLegacyProblemProgress(
                        problemId,
                        "WRONG",
                        2,
                        null
                );

        Instant correctSolvedAt =
                Instant.parse(
                        "2026-09-01T12:00:00Z"
                );

        UUID correctProgressId =
                insertLegacyProblemProgress(
                        problemId,
                        "CORRECT",
                        3,
                        correctSolvedAt
                );

        assertThat(countProblemProgress())
                .isEqualTo(2);

        assertThat(countProblem(problemId))
                .isEqualTo(1);

        Instant beforeMigration =
                getCurrentDatabaseTime();

        // when
        assertThatCode(
                () -> migrateTo("15")
        ).doesNotThrowAnyException();

        Instant afterMigration =
                getCurrentDatabaseTime();

        // then
        assertThat(countProblemProgress())
                .isEqualTo(2);

        assertThat(countProblem(problemId))
                .isEqualTo(1);

        ProblemProgressSnapshot wrongProgress =
                getProblemProgress(
                        wrongProgressId
                );

        assertThat(wrongProgress.progressStatus())
                .isEqualTo("WRONG");

        assertThat(wrongProgress.versionNo())
                .isEqualTo(2);

        assertThat(wrongProgress.solvedAt())
                .isNull();

        assertThat(wrongProgress.attemptNo())
                .isEqualTo(1);

        assertThat(wrongProgress.lockVersion())
                .isEqualTo(1L);

        assertThat(wrongProgress.createdAt())
                .isNotNull();

        assertThat(
                wrongProgress.createdAt()
                        .isBefore(
                                beforeMigration
                        )
        ).isFalse();

        assertThat(
                wrongProgress.createdAt()
                        .isAfter(
                                afterMigration
                        )
        ).isFalse();

        ProblemProgressSnapshot correctProgress =
                getProblemProgress(
                        correctProgressId
                );

        assertThat(correctProgress.progressStatus())
                .isEqualTo("CORRECT");

        assertThat(correctProgress.versionNo())
                .isEqualTo(3);

        assertThat(correctProgress.solvedAt())
                .isEqualTo(correctSolvedAt);

        assertThat(correctProgress.attemptNo())
                .isEqualTo(1);

        assertThat(correctProgress.lockVersion())
                .isEqualTo(1L);

        assertThat(correctProgress.createdAt())
                .isNotNull();

        assertThat(
                correctProgress.createdAt()
                        .isBefore(
                                beforeMigration
                        )
        ).isFalse();

        assertThat(
                correctProgress.createdAt()
                        .isAfter(
                                afterMigration
                        )
        ).isFalse();

        assertThat(
                isColumnNullable(
                        "created_at"
                )
        ).isFalse();

        assertThat(
                isColumnNullable(
                        "attempt_no"
                )
        ).isFalse();

        assertThat(
                isColumnNullable(
                        "lock_version"
                )
        ).isFalse();

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

    @Test
    @DisplayName(
            "기존 NOT_ATTEMPTED ProblemProgress만 제거되고 "
                    + "WRONG/CORRECT 데이터는 유지된다"
    )
    void migrateV15_withNotAttempted_removesOnlyNotAttempted()
            throws SQLException {

        // given
        migrateTo("14");

        UUID problemId =
                UUID.randomUUID();

        insertLegacyProblem(
                problemId
        );

        UUID notAttemptedProgressId =
                insertLegacyProblemProgress(
                        problemId,
                        "NOT_ATTEMPTED",
                        1,
                        null
                );

        UUID wrongProgressId =
                insertLegacyProblemProgress(
                        problemId,
                        "WRONG",
                        1,
                        null
                );

        UUID correctProgressId =
                insertLegacyProblemProgress(
                        problemId,
                        "CORRECT",
                        1,
                        Instant.parse(
                                "2026-09-01T12:00:00Z"
                        )
                );

        assertThat(countProblemProgress())
                .isEqualTo(3);

        // when
        assertThatCode(
                () -> migrateTo("15")
        ).doesNotThrowAnyException();

        // then
        assertThat(countProblemProgress())
                .isEqualTo(2);

        assertThat(
                existsProblemProgress(
                        notAttemptedProgressId
                )
        ).isFalse();

        assertThat(
                existsProblemProgress(
                        wrongProgressId
                )
        ).isTrue();

        assertThat(
                existsProblemProgress(
                        correctProgressId
                )
        ).isTrue();

        assertThat(countProblem(problemId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "V15 이후 NOT_ATTEMPTED 상태와 "
                    + "1 미만 attempt_no는 저장할 수 없다"
    )
    void migrateV15_constraints_rejectInvalidStatusAndAttemptNo()
            throws SQLException {

        // given
        migrateTo("15");

        UUID problemId =
                UUID.randomUUID();

        insertLegacyProblem(
                problemId
        );

        // when & then
        assertThatThrownBy(
                () -> insertV15ProblemProgress(
                        problemId,
                        "NOT_ATTEMPTED",
                        1
                )
        ).isInstanceOf(
                SQLException.class
        );

        assertThatThrownBy(
                () -> insertV15ProblemProgress(
                        problemId,
                        "WRONG",
                        0
                )
        ).isInstanceOf(
                SQLException.class
        );

        assertThat(countProblemProgress())
                .isZero();
    }

    private void migrateTo(
            String version
    ) {

        Flyway flyway =
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
                        .load();

        flyway.migrate();
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

    private UUID insertLegacyProblemProgress(
            UUID problemId,
            String progressStatus,
            int versionNo,
            Instant solvedAt
    ) throws SQLException {

        UUID problemProgressId =
                UUID.randomUUID();

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
                                VALUES (?, ?, ?, ?, ?, ?)
                                """
                        )
        ) {
            statement.setObject(
                    1,
                    problemProgressId
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
                    versionNo
            );

            if (solvedAt == null) {
                statement.setNull(
                        5,
                        java.sql.Types.TIMESTAMP_WITH_TIMEZONE
                );
            } else {
                statement.setTimestamp(
                        5,
                        Timestamp.from(
                                solvedAt
                        )
                );
            }

            statement.setString(
                    6,
                    progressStatus
            );

            statement.executeUpdate();
        }

        return problemProgressId;
    }

    private void insertV15ProblemProgress(
            UUID problemId,
            String progressStatus,
            int attemptNo
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
                                    progress_status,
                                    created_at,
                                    attempt_no,
                                    lock_version
                                )
                                VALUES (
                                    ?, ?, ?, ?, NULL, ?, CURRENT_TIMESTAMP, ?, ?
                                )
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
                    progressStatus
            );

            statement.setInt(
                    6,
                    attemptNo
            );

            statement.setLong(
                    7,
                    1L
            );

            statement.executeUpdate();
        }
    }

    private ProblemProgressSnapshot getProblemProgress(
            UUID problemProgressId
    ) throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT
                                    version_no,
                                    solved_at,
                                    progress_status,
                                    created_at,
                                    attempt_no,
                                    lock_version
                                FROM content_schema.p_problem_progress
                                WHERE id = ?
                                """
                        )
        ) {
            statement.setObject(
                    1,
                    problemProgressId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                assertThat(
                        resultSet.next()
                ).isTrue();

                Timestamp solvedAt =
                        resultSet.getTimestamp(
                                "solved_at"
                        );

                Timestamp createdAt =
                        resultSet.getTimestamp(
                                "created_at"
                        );

                return new ProblemProgressSnapshot(
                        resultSet.getInt(
                                "version_no"
                        ),
                        solvedAt == null
                                ? null
                                : solvedAt.toInstant(),
                        resultSet.getString(
                                "progress_status"
                        ),
                        createdAt == null
                                ? null
                                : createdAt.toInstant(),
                        resultSet.getInt(
                                "attempt_no"
                        ),
                        resultSet.getLong(
                                "lock_version"
                        )
                );
            }
        }
    }

    private boolean existsProblemProgress(
            UUID problemProgressId
    ) throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM content_schema.p_problem_progress
                                    WHERE id = ?
                                )
                                """
                        )
        ) {
            statement.setObject(
                    1,
                    problemProgressId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                resultSet.next();

                return resultSet.getBoolean(
                        1
                );
            }
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

            return resultSet.getInt(
                    1
            );
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

                return resultSet.getInt(
                        1
                );
            }
        }
    }

    private Instant getCurrentDatabaseTime()
            throws SQLException {

        try (
                Connection connection =
                        postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT CURRENT_TIMESTAMP
                                """
                        );
                ResultSet resultSet =
                        statement.executeQuery()
        ) {
            resultSet.next();

            return resultSet.getTimestamp(
                    1
            ).toInstant();
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
                assertThat(
                        resultSet.next()
                ).isTrue();

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

                return resultSet.getBoolean(
                        1
                );
            }
        }
    }

    private record ProblemProgressSnapshot(
            int versionNo,
            Instant solvedAt,
            String progressStatus,
            Instant createdAt,
            int attemptNo,
            long lockVersion
    ) {
    }
}