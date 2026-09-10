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
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class UserSchemaMigrationIntegrationTest {

    private static final String USER_SCHEMA = "user_schema";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Test
    @DisplayName(
            "Flyway V1부터 V3까지 적용하면 "
                    + "email_lookup_hash가 VARCHAR(64)로 변경된다"
    )
    void migratesEmailLookupHashFromCharToVarchar() throws Exception {
        // given
        Flyway flywayToV2 = Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .schemas(USER_SCHEMA)
                .locations("classpath:db/migration")
                .target("2")
                .load();

        assertThat(
                flywayToV2.migrate().migrationsExecuted
        ).isEqualTo(2);

        String emailLookupHash = "a".repeat(64);

        try (
                Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                );
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO user_schema.p_users (
                            email,
                            email_lookup_hash,
                            password_hash,
                            nickname,
                            created_by,
                            updated_by
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """
                )
        ) {
            UUID actorId = UUID.randomUUID();

            statement.setString(1, "encrypted-email");
            statement.setString(2, emailLookupHash);
            statement.setString(3, "password-hash");
            statement.setString(4, "MigrationUser");
            statement.setObject(5, actorId);
            statement.setObject(6, actorId);

            statement.executeUpdate();
        }

        // when
        Flyway flywayToV3 = Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .schemas(USER_SCHEMA)
                .locations("classpath:db/migration")
                .target("3")
                .load();

        assertThat(
                flywayToV3.migrate().migrationsExecuted
        ).isEqualTo(1);

        // then
        try (
                Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
        ) {
            assertEmailLookupHashColumn(connection);
            assertExistingHashPreserved(
                    connection,
                    emailLookupHash
            );
            assertUniqueIndexPreserved(connection);
        }
    }

    private void assertEmailLookupHashColumn(
            Connection connection
    ) throws Exception {
        try (
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT data_type,
                               character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = ?
                          AND table_name = 'p_users'
                          AND column_name = 'email_lookup_hash'
                        """
                )
        ) {
            statement.setString(1, USER_SCHEMA);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();

                assertThat(
                        resultSet.getString("data_type")
                ).isEqualTo("character varying");

                assertThat(
                        resultSet.getInt(
                                "character_maximum_length"
                        )
                ).isEqualTo(64);
            }
        }
    }

    private void assertExistingHashPreserved(
            Connection connection,
            String expectedHash
    ) throws Exception {
        try (
                Statement statement =
                        connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        """
                        SELECT email_lookup_hash
                        FROM user_schema.p_users
                        WHERE nickname = 'MigrationUser'
                        """
                )
        ) {
            assertThat(resultSet.next()).isTrue();

            assertThat(
                    resultSet.getString("email_lookup_hash")
            ).isEqualTo(expectedHash);
        }
    }

    private void assertUniqueIndexPreserved(
            Connection connection
    ) throws Exception {
        try (
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = ?
                          AND tablename = 'p_users'
                          AND indexname =
                              'uk_p_users_active_email_lookup_hash'
                        """
                )
        ) {
            statement.setString(1, USER_SCHEMA);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
            }
        }
    }
}
