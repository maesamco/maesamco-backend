package com.maesamco.user.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SocialAccount 테이블을 추가하는 Flyway V9 마이그레이션을
 * 실제 PostgreSQL에서 검증합니다.
 */
@Testcontainers
class SocialAccountV9MigrationTest {

    private static final String USER_SCHEMA =
            "user_schema";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Test
    @DisplayName(
            "V9 적용 시 SocialAccount 테이블과 고유 인덱스를 생성한다"
    )
    void migrateV9() throws Exception {
        // given
        Flyway flywayToV8 =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword()
                        )
                        .schemas(USER_SCHEMA)
                        .locations(
                                "classpath:db/migration"
                        )
                        .target("8")
                        .load();

        assertThat(
                flywayToV8
                        .migrate()
                        .migrationsExecuted
        ).isEqualTo(8);

        // when
        Flyway flywayToV9 =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword()
                        )
                        .schemas(USER_SCHEMA)
                        .locations(
                                "classpath:db/migration"
                        )
                        .target("9")
                        .load();

        assertThat(
                flywayToV9
                        .migrate()
                        .migrationsExecuted
        ).isEqualTo(1);

        // then
        try (
                Connection connection =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword()
                        )
        ) {
            assertTableExists(connection);

            assertColumn(
                    connection,
                    "provider",
                    "character varying",
                    20
            );

            assertColumn(
                    connection,
                    "provider_user_id",
                    "character varying",
                    255
            );

            assertIndexExists(
                    connection,
                    "uk_p_social_accounts_active_provider_user"
            );

            assertIndexExists(
                    connection,
                    "uk_p_social_accounts_active_user_provider"
            );

            assertIndexExists(
                    connection,
                    "idx_p_social_accounts_active_user"
            );
        }
    }

    private void assertTableExists(
            Connection connection
    ) throws Exception {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT table_name
                                FROM information_schema.tables
                                WHERE table_schema = ?
                                  AND table_name =
                                      'p_social_accounts'
                                """
                        )
        ) {
            statement.setString(
                    1,
                    USER_SCHEMA
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                assertThat(
                        resultSet.next()
                ).isTrue();
            }
        }
    }

    private void assertColumn(
            Connection connection,
            String columnName,
            String expectedDataType,
            int expectedLength
    ) throws Exception {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT data_type,
                                       character_maximum_length
                                FROM information_schema.columns
                                WHERE table_schema = ?
                                  AND table_name =
                                      'p_social_accounts'
                                  AND column_name = ?
                                """
                        )
        ) {
            statement.setString(
                    1,
                    USER_SCHEMA
            );

            statement.setString(
                    2,
                    columnName
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                assertThat(
                        resultSet.next()
                ).isTrue();

                assertThat(
                        resultSet.getString(
                                "data_type"
                        )
                ).isEqualTo(
                        expectedDataType
                );

                assertThat(
                        resultSet.getInt(
                                "character_maximum_length"
                        )
                ).isEqualTo(
                        expectedLength
                );
            }
        }
    }

    private void assertIndexExists(
            Connection connection,
            String indexName
    ) throws Exception {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT indexname
                                FROM pg_indexes
                                WHERE schemaname = ?
                                  AND tablename =
                                      'p_social_accounts'
                                  AND indexname = ?
                                """
                        )
        ) {
            statement.setString(
                    1,
                    USER_SCHEMA
            );

            statement.setString(
                    2,
                    indexName
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {
                assertThat(
                        resultSet.next()
                ).isTrue();
            }
        }
    }
}
