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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("V18 legacy non-CODE Problem migration 통합 테스트")
class ProblemTypeV18MigrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Test
    @DisplayName(
            "legacy non-CODE 문제는 ARCHIVED로 보존하고 "
                    + "이후 활성 non-CODE 문제 생성을 차단한다"
    )
    void migrateV18_archivesLegacyNonCodeAndRejectsNewActiveNonCode()
            throws Exception {

        // given
        migrateTo("17");

        UUID legacyProblemId =
                UUID.randomUUID();

        insertProblem(
                legacyProblemId,
                "SHORT_ANSWER",
                "REVIEW_PENDING"
        );

        assertThat(
                findProblemStatus(
                        legacyProblemId
                )
        ).isEqualTo(
                "REVIEW_PENDING"
        );

        // when
        migrateTo("18");

        // then
        assertThat(
                findProblemStatus(
                        legacyProblemId
                )
        ).isEqualTo(
                "ARCHIVED"
        );

        /*
         * 현재 도메인이 지원하는 CODE 문제는
         * 활성 상태로 정상 저장할 수 있다.
         */
        UUID codeProblemId =
                UUID.randomUUID();

        insertProblem(
                codeProblemId,
                "CODE",
                "REVIEW_PENDING"
        );

        assertThat(
                findProblemStatus(
                        codeProblemId
                )
        ).isEqualTo(
                "REVIEW_PENDING"
        );

        /*
         * V18 이후 신규 활성 non-CODE 문제는
         * DB CHECK 제약에서 거부한다.
         */
        assertThatThrownBy(
                () -> insertProblem(
                        UUID.randomUUID(),
                        "MULTIPLE_CHOICE",
                        "REVIEW_PENDING"
                )
        )
                .isInstanceOf(
                        SQLException.class
                );

        /*
         * legacy 보존 용도의 ARCHIVED non-CODE는 허용한다.
         */
        UUID archivedProblemId =
                UUID.randomUUID();

        insertProblem(
                archivedProblemId,
                "FILL_IN_BLANK",
                "ARCHIVED"
        );

        assertThat(
                findProblemStatus(
                        archivedProblemId
                )
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

    private void insertProblem(
            UUID problemId,
            String type,
            String status
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
                    ?,
                    ?,
                    ?,
                    'DEFAULT',
                    'EASY',
                    'HUMAN_AUTHORED',
                    ?,
                    'public class Main {}',
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
                    type
            );

            statement.setString(
                    5,
                    status
            );

            statement.setObject(
                    6,
                    actorId
            );

            statement.setObject(
                    7,
                    actorId
            );

            statement.executeUpdate();
        }
    }

    private String findProblemStatus(
            UUID problemId
    ) throws SQLException {

        String sql = """
                SELECT problem_status
                FROM content_schema.p_problems
                WHERE id = ?
                """;

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
                        "problem_status"
                );
            }
        }
    }
}
