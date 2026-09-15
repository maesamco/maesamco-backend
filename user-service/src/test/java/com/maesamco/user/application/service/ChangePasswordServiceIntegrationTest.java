package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSessionLogoutAllStore;
import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import com.maesamco.user.infrastructure.persistence.UserRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 비밀번호 변경 서비스의 실제 PostgreSQL 연동과
 * 트랜잭션 동작을 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        ChangePasswordService.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChangePasswordServiceIntegrationTest {

    private static final String ENCRYPTED_EMAIL =
            "encrypted-email";

    private static final String EMAIL =
            "learner@example.com";

    private static final String CURRENT_PASSWORD =
            "Oldpass123!";

    private static final String NEW_PASSWORD =
            "Newpass123!";

    private static final String OLD_PASSWORD_HASH =
            "old-password-hash";

    private static final String NEW_PASSWORD_HASH =
            "new-password-hash";

    private static final Instant NOW =
            Instant.parse("2026-09-15T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private ChangePasswordService changePasswordService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private PasswordHasher passwordHasher;

    @MockitoBean
    private EmailCipher emailCipher;

    @MockitoBean
    private AuthSessionLogoutAllStore authSessionLogoutAllStore;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName(
            "비밀번호 변경 성공 시 새 비밀번호 해시를 저장하고 "
                    + "모든 인증 세션을 무효화한다"
    )
    void changePassword_updatesHashAndInvalidatesSessions() {
        // given
        User user = createUser(
                "a".repeat(64),
                "ChangeSuccess"
        );

        stubSuccessfulDependencies();

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        // when
        changePasswordService.changePassword(
                user.getId(),
                command
        );

        // then
        User updatedUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        assertThat(updatedUser.getPasswordHash())
                .isEqualTo(NEW_PASSWORD_HASH);

        verify(authSessionLogoutAllStore)
                .logoutAll(user.getId(), NOW);
    }

    @Test
    @DisplayName(
            "인증 세션 무효화에 실패하면 비밀번호 변경을 롤백한다"
    )
    void changePassword_rollsBackWhenSessionInvalidationFails() {
        // given
        User user = createUser(
                "b".repeat(64),
                "ChangeRollback"
        );

        stubSuccessfulDependencies();

        doThrow(
                new IllegalStateException(
                        "인증 세션 무효화 실패"
                )
        ).when(authSessionLogoutAllStore)
                .logoutAll(user.getId(), NOW);

        ChangePasswordCommand command =
                new ChangePasswordCommand(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD
                );

        // when & then
        assertThatThrownBy(
                () -> changePasswordService.changePassword(
                        user.getId(),
                        command
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("인증 세션 무효화 실패");

        User rolledBackUser = userRepository
                .findById(user.getId())
                .orElseThrow();

        assertThat(rolledBackUser.getPasswordHash())
                .isEqualTo(OLD_PASSWORD_HASH);
    }

    private User createUser(
            String emailLookupHash,
            String nickname
    ) {
        return userRepository.save(
                User.create(
                        ENCRYPTED_EMAIL,
                        emailLookupHash,
                        OLD_PASSWORD_HASH,
                        nickname,
                        3,
                        LearningLevel.BEGINNER
                )
        );
    }

    private void stubSuccessfulDependencies() {
        when(
                passwordHasher.matches(
                        CURRENT_PASSWORD,
                        OLD_PASSWORD_HASH
                )
        ).thenReturn(true);

        when(
                passwordHasher.matches(
                        NEW_PASSWORD,
                        OLD_PASSWORD_HASH
                )
        ).thenReturn(false);

        when(emailCipher.decrypt(ENCRYPTED_EMAIL))
                .thenReturn(EMAIL);

        when(passwordHasher.hash(NEW_PASSWORD))
                .thenReturn(NEW_PASSWORD_HASH);

        when(clock.instant())
                .thenReturn(NOW);
    }
}
