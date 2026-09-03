package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import com.maesamco.user.infrastructure.persistence.UserGamificationStateRepositoryImpl;
import com.maesamco.user.infrastructure.persistence.UserRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회원가입 애플리케이션 서비스의 실제 PostgreSQL 트랜잭션을 검증합니다.
 *
 * <p>User와 UserGamificationState가 같은 트랜잭션에서 생성되는지 확인하고,
 * 회원가입 처리 중 후속 단계에서 예외가 발생하면 두 데이터가 모두
 * 롤백되는지 검증합니다.</p>
 *
 * <p>Redis 저장소 자체의 동작은 별도 통합 테스트에서 검증하므로
 * 이 테스트에서는 AuthSessionStore를 Mock으로 사용하여
 * DB 트랜잭션 경계와 보상 처리를 집중적으로 검증합니다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UserGamificationStateRepositoryImpl.class,
        SignUpService.class
})
@Sql(
        scripts = "/db/migration/V2__add_active_user_unique_indexes.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SignUpServiceIntegrationTest {

    private static final String NORMALIZED_EMAIL =
            "learner@example.com";

    private static final String ENCRYPTED_EMAIL =
            "encrypted-email";

    private static final String PASSWORD_HASH =
            "argon2-password-hash";

    private static final String ACCESS_TOKEN =
            "access-token";

    private static final String REFRESH_TOKEN =
            "refresh-token";

    private static final String REFRESH_TOKEN_HASH =
            "refresh-token-hash";

    private static final Instant NOW =
            Instant.parse("2026-09-03T00:00:00Z");

    private static final Instant ACCESS_TOKEN_EXPIRES_AT =
            NOW.plusSeconds(900);

    private static final Instant REFRESH_TOKEN_EXPIRES_AT =
            NOW.plusSeconds(
                    60L * 60 * 24 * 14
            );

    /**
     * 실제 PostgreSQL 통합 테스트에 사용할 임시 DB입니다.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse(
                            "postgres:16-alpine"
                    )
            );

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGamificationStateRepository
            gamificationStateRepository;

    @MockitoBean
    private EmailNormalizer emailNormalizer;

    @MockitoBean
    private EmailCipher emailCipher;

    @MockitoBean
    private EmailLookupHasher emailLookupHasher;

    @MockitoBean
    private PasswordHasher passwordHasher;

    @MockitoBean
    private TokenIssuer tokenIssuer;

    @MockitoBean
    private RefreshTokenHasher refreshTokenHasher;

    @MockitoBean
    private AuthSessionStore authSessionStore;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName(
            "회원가입 성공 시 User와 초기 게이미피케이션 상태를 "
                    + "같은 트랜잭션으로 저장한다"
    )
    void signUp_commitsUserAndGamificationState() {
        // given
        String emailLookupHash =
                "a".repeat(64);

        SignUpCommand command =
                createCommand(
                        "SuccessUser"
                );

        stubSuccessfulDependencies(
                command,
                emailLookupHash
        );

        // when
        SignUpResult result =
                signUpService.signUp(command);

        // then
        User savedUser =
                userRepository
                        .findById(result.userId())
                        .orElseThrow();

        assertThat(savedUser.getId())
                .isEqualTo(result.userId());

        assertThat(savedUser.getEncryptedEmail())
                .isEqualTo(ENCRYPTED_EMAIL);

        assertThat(savedUser.getEmailLookupHash())
                .isEqualTo(emailLookupHash);

        assertThat(savedUser.getPasswordHash())
                .isEqualTo(PASSWORD_HASH);

        assertThat(savedUser.getNickname())
                .isEqualTo("SuccessUser");

        assertThat(savedUser.getRole())
                .isEqualTo(UserRole.USER);

        assertThat(savedUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(savedUser.getJavaExperienceMonths())
                .isEqualTo(3);

        assertThat(savedUser.getLearningLevel())
                .isEqualTo(LearningLevel.BEGINNER);

        UserGamificationState gamificationState =
                gamificationStateRepository
                        .findByUserId(result.userId())
                        .orElseThrow();

        assertThat(gamificationState.getUserId())
                .isEqualTo(result.userId());

        assertThat(gamificationState.getTotalXp())
                .isZero();

        assertThat(gamificationState.getLevel())
                .isEqualTo(1);

        assertThat(gamificationState.getCurrentStreak())
                .isZero();

        assertThat(gamificationState.getLongestStreak())
                .isZero();

        assertThat(gamificationState.getLastActivityDate())
                .isNull();

        assertThat(result.accessToken())
                .isEqualTo(ACCESS_TOKEN);

        assertThat(result.accessTokenExpiresIn())
                .isEqualTo(900);

        var authSessionCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        AuthSession.class
                );

        verify(authSessionStore)
                .save(
                        authSessionCaptor.capture()
                );

        AuthSession authSession =
                authSessionCaptor.getValue();

        assertThat(authSession.sessionId())
                .isNotNull();

        assertThat(authSession.familyId())
                .isNotNull();

        assertThat(authSession.userId())
                .isEqualTo(result.userId());

        assertThat(authSession.refreshTokenHash())
                .isEqualTo(REFRESH_TOKEN_HASH);

        assertThat(authSession.createdAt())
                .isEqualTo(NOW);

        assertThat(authSession.expiresAt())
                .isEqualTo(
                        REFRESH_TOKEN_EXPIRES_AT
                );
    }

    @Test
    @DisplayName(
            "인증 세션 저장 중 실패하면 User와 "
                    + "게이미피케이션 상태를 모두 롤백한다"
    )
    void signUp_rollsBackDatabaseWhenAuthSessionSaveFails() {
        // given
        String emailLookupHash =
                "b".repeat(64);

        SignUpCommand command =
                createCommand(
                        "RollbackUser"
                );

        stubSuccessfulDependencies(
                command,
                emailLookupHash
        );

        doThrow(
                new IllegalStateException(
                        "Redis 인증 세션 저장 실패"
                )
        ).when(authSessionStore)
                .save(
                        any(AuthSession.class)
                );

        // when & then
        assertThatThrownBy(
                () -> signUpService.signUp(command)
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "Redis 인증 세션 저장 실패"
                );

        var authSessionCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        AuthSession.class
                );

        verify(authSessionStore)
                .save(
                        authSessionCaptor.capture()
                );

        AuthSession attemptedSession =
                authSessionCaptor.getValue();

        UUID userId =
                attemptedSession.userId();

        assertThat(
                userRepository.findById(userId)
        ).isEmpty();

        assertThat(
                gamificationStateRepository
                        .findByUserId(userId)
        ).isEmpty();

        verify(authSessionStore)
                .deleteBySessionId(
                        attemptedSession.sessionId()
                );
    }

    /**
     * 테스트에서 사용할 정상 회원가입 명령을 생성합니다.
     */
    private SignUpCommand createCommand(
            String nickname
    ) {
        return new SignUpCommand(
                "Learner@Example.com",
                "Abcd1234!",
                nickname,
                3,
                LearningLevel.BEGINNER
        );
    }

    /**
     * 회원가입 트랜잭션 검증에 필요한 외부 의존성의
     * 정상 동작을 구성합니다.
     */
    private void stubSuccessfulDependencies(
            SignUpCommand command,
            String emailLookupHash
    ) {
        when(
                emailNormalizer.normalize(
                        command.email()
                )
        ).thenReturn(
                NORMALIZED_EMAIL
        );

        when(
                emailLookupHasher.hash(
                        NORMALIZED_EMAIL
                )
        ).thenReturn(
                emailLookupHash
        );

        when(
                emailCipher.encrypt(
                        NORMALIZED_EMAIL
                )
        ).thenReturn(
                ENCRYPTED_EMAIL
        );

        when(
                passwordHasher.hash(
                        command.password()
                )
        ).thenReturn(
                PASSWORD_HASH
        );

        IssuedTokens issuedTokens =
                new IssuedTokens(
                        ACCESS_TOKEN,
                        ACCESS_TOKEN_EXPIRES_AT,
                        REFRESH_TOKEN,
                        REFRESH_TOKEN_EXPIRES_AT
                );

        when(
                tokenIssuer.issueTokens(
                        any(UUID.class),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(
                issuedTokens
        );

        when(
                refreshTokenHasher.hash(
                        REFRESH_TOKEN
                )
        ).thenReturn(
                REFRESH_TOKEN_HASH
        );

        when(clock.instant())
                .thenReturn(NOW);
    }
}
