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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소셜 사용자의 비밀번호 미설정을 허용하는 Flyway V10 마이그레이션을
 * 실제 PostgreSQL에서 검증합니다(#308).
 */
@Testcontainers
class UserPasswordNullableV10MigrationTest {

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
            "V10 적용 시 기존 사용자의 비밀번호 해시는 유지되고 "
                    + "password_hash에 NULL을 저장할 수 있다"
    )
    void migrateV10() throws Exception {
        // given — V9까지 적용하고 비밀번호가 있는 기존 사용자를 저장한다.
        assertThat(
                flyway("9").migrate().migrationsExecuted
        ).isEqualTo(9);

        UUID existingUserId = UUID.randomUUID();

        try (Connection connection = connect()) {
            assertThat(isPasswordNullable(connection)).isFalse();

            insertUser(
                    connection,
                    existingUserId,
                    "a".repeat(64),
                    "ExistingUser",
                    "argon2-password-hash"
            );
        }

        // when
        assertThat(
                flyway("10").migrate().migrationsExecuted
        ).isEqualTo(1);

        // then
        try (Connection connection = connect()) {
            assertThat(isPasswordNullable(connection)).isTrue();

            assertThat(readPasswordHash(connection, existingUserId))
                    .isEqualTo("argon2-password-hash");

            UUID socialUserId = UUID.randomUUID();

            insertUser(
                    connection,
                    socialUserId,
                    "b".repeat(64),
                    "SocialUser",
                    null
            );

            assertThat(readPasswordHash(connection, socialUserId))
                    .isNull();
        }
    }

    private Flyway flyway(
            String target
    ) {
        return Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .schemas(USER_SCHEMA)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    private boolean isPasswordNullable(
            Connection connection
    ) throws Exception {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT is_nullable
                                FROM information_schema.columns
                                WHERE table_schema = ?
                                  AND table_name = 'p_users'
                                  AND column_name = 'password_hash'
                                """
                        )
        ) {
            statement.setString(1, USER_SCHEMA);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return "YES".equals(resultSet.getString("is_nullable"));
            }
        }
    }

    private void insertUser(
            Connection connection,
            UUID userId,
            String emailLookupHash,
            String nickname,
            String passwordHash
    ) throws Exception {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                INSERT INTO user_schema.p_users (
                                    id,
                                    email,
                                    email_lookup_hash,
                                    password_hash,
                                    nickname,
                                    created_by,
                                    updated_by
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?)
                                """
                        )
        ) {
            UUID actorId = UUID.randomUUID();

            statement.setObject(1, userId);
            statement.setString(2, "encrypted-email");
            statement.setString(3, emailLookupHash);
            statement.setString(4, passwordHash);
            statement.setString(5, nickname);
            statement.setObject(6, actorId);
            statement.setObject(7, actorId);

            statement.executeUpdate();
        }
    }

    private String readPasswordHash(
            Connection connection,
            UUID userId
    ) throws Exception {
        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT password_hash FROM user_schema.p_users WHERE id = ?"
                        )
        ) {
            statement.setObject(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("password_hash");
            }
        }
    }
}
