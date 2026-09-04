package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoginService의 로그인 인증 및 세션 생성 흐름을 검증하는 단위 테스트입니다.
 *
 * <p>이메일 조회, 비밀번호 검증, 사용자 상태 확인,
 * 토큰 발급 및 Redis 인증 세션 저장까지의 흐름을 검증합니다.</p>
 *
 * <p>또한 사용자 존재 여부에 따른 비밀번호 검증 비용 차이를 줄이기 위한
 * 더미 비밀번호 검증과 로그인 결과 메트릭도 함께 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String LOGIN_METRIC_NAME =
            "user.auth.login";

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private EmailLookupHasher emailLookupHasher;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private Clock clock;

    private SimpleMeterRegistry meterRegistry;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        loginService = new LoginService(
                emailNormalizer,
                emailLookupHasher,
                passwordHasher,
                userRepository,
                tokenIssuer,
                refreshTokenHasher,
                authSessionStore,
                meterRegistry,
                clock
        );
    }

    @Test
    @DisplayName(
            "로그인에 성공하면 토큰을 발급하고 "
                    + "Redis 인증 세션과 성공 메트릭을 기록한다"
    )
    void login() {
        // given
        String rawEmail =
                " Learner@Example.com ";

        String trimmedEmail =
                "Learner@Example.com";

        String normalizedEmail =
                "learner@example.com";

        String emailLookupHash =
                "a".repeat(64);

        String rawPassword =
                "Abcd1234!";

        String passwordHash =
                "argon2-password-hash";

        String refreshTokenHash =
                "refresh-token-hash";

        User user = createUser(
                "encrypted-email",
                emailLookupHash,
                passwordHash
        );

        Instant now =
                Instant.parse(
                        "2026-09-03T01:00:00Z"
                );

        Instant accessTokenExpiresAt =
                now.plusSeconds(900);

        Instant refreshTokenExpiresAt =
                now.plusSeconds(
                        60L * 60 * 24 * 7
                );

        IssuedTokens issuedTokens =
                new IssuedTokens(
                        "access-token",
                        accessTokenExpiresAt,
                        "refresh-token",
                        refreshTokenExpiresAt
                );

        LoginCommand command =
                new LoginCommand(
                        rawEmail,
                        rawPassword
                );

        when(
                emailNormalizer.normalize(
                        trimmedEmail
                )
        ).thenReturn(
                normalizedEmail
        );

        when(
                emailLookupHasher.hash(
                        normalizedEmail
                )
        ).thenReturn(
                emailLookupHash
        );

        when(
                userRepository
                        .findByEmailLookupHash(
                                emailLookupHash
                        )
        ).thenReturn(
                Optional.of(user)
        );

        when(
                passwordHasher.matches(
                        rawPassword,
                        passwordHash
                )
        ).thenReturn(true);

        when(
                tokenIssuer.issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(
                issuedTokens
        );

        when(
                refreshTokenHasher.hash(
                        "refresh-token"
                )
        ).thenReturn(
                refreshTokenHash
        );

        when(clock.instant())
                .thenReturn(now);

        // when
        LoginResult result =
                loginService.login(command);

        // then
        verify(emailNormalizer)
                .normalize(
                        trimmedEmail
                );

        verify(emailLookupHasher)
                .hash(
                        normalizedEmail
                );

        verify(userRepository)
                .findByEmailLookupHash(
                        emailLookupHash
                );

        verify(passwordHasher)
                .matches(
                        rawPassword,
                        passwordHash
                );

        ArgumentCaptor<UUID> sessionIdCaptor =
                ArgumentCaptor.forClass(
                        UUID.class
                );

        verify(tokenIssuer)
                .issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        sessionIdCaptor.capture()
                );

        ArgumentCaptor<AuthSession>
                authSessionCaptor =
                ArgumentCaptor.forClass(
                        AuthSession.class
                );

        verify(authSessionStore)
                .save(
                        authSessionCaptor.capture()
                );

        AuthSession authSession =
                authSessionCaptor.getValue();

        assertThat(
                authSession.sessionId()
        ).isEqualTo(
                sessionIdCaptor.getValue()
        );

        assertThat(
                authSession.familyId()
        ).isNotNull();

        assertThat(
                authSession.userId()
        ).isEqualTo(
                user.getId()
        );

        assertThat(
                authSession.refreshTokenHash()
        ).isEqualTo(
                refreshTokenHash
        );

        assertThat(
                authSession.refreshTokenHash()
        ).isNotEqualTo(
                "refresh-token"
        );

        assertThat(
                authSession.createdAt()
        ).isEqualTo(now);

        assertThat(
                authSession.expiresAt()
        ).isEqualTo(
                refreshTokenExpiresAt
        );

        assertThat(
                result.userId()
        ).isEqualTo(
                user.getId()
        );

        assertThat(
                result.nickname()
        ).isEqualTo(
                "김티암"
        );

        assertThat(
                result.role()
        ).isEqualTo(
                UserRole.USER
        );

        assertThat(
                result.status()
        ).isEqualTo(
                UserStatus.ACTIVE
        );

        assertThat(
                result.javaExperienceMonths()
        ).isEqualTo(3);

        assertThat(
                result.learningLevel()
        ).isEqualTo(
                LearningLevel.BEGINNER
        );

        assertThat(
                result.accessToken()
        ).isEqualTo(
                "access-token"
        );

        assertThat(
                result.accessTokenExpiresIn()
        ).isEqualTo(900);

        assertThat(
                result.issuedTokens()
        ).isSameAs(
                issuedTokens
        );

        assertThat(
                loginMetricCount(
                        "success"
                )
        ).isEqualTo(1.0);

        assertThat(
                loginMetricCount(
                        "invalid_credentials"
                )
        ).isZero();

        assertThat(
                loginMetricCount(
                        "inactive_user"
                )
        ).isZero();
    }

    @Test
    @DisplayName(
            "사용자가 없어도 더미 비밀번호 검증 후 "
                    + "INVALID_CREDENTIALS를 반환한다"
    )
    void login_userNotFound() {
        // given
        String trimmedEmail =
                "Learner@Example.com";

        String normalizedEmail =
                "learner@example.com";

        String emailLookupHash =
                "b".repeat(64);

        String rawPassword =
                "Abcd1234!";

        LoginCommand command =
                new LoginCommand(
                        " Learner@Example.com ",
                        rawPassword
                );

        when(
                emailNormalizer.normalize(
                        trimmedEmail
                )
        ).thenReturn(
                normalizedEmail
        );

        when(
                emailLookupHasher.hash(
                        normalizedEmail
                )
        ).thenReturn(
                emailLookupHash
        );

        when(
                userRepository
                        .findByEmailLookupHash(
                                emailLookupHash
                        )
        ).thenReturn(
                Optional.empty()
        );

        // when & then
        assertThatThrownBy(
                () -> loginService.login(command)
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_CREDENTIALS
                );

        verify(passwordHasher)
                .matches(
                        eq(rawPassword),
                        anyString()
                );

        verify(
                tokenIssuer,
                never()
        ).issueTokens(
                any(),
                any(),
                any()
        );

        verify(
                authSessionStore,
                never()
        ).save(
                any(AuthSession.class)
        );

        assertThat(
                loginMetricCount(
                        "invalid_credentials"
                )
        ).isEqualTo(1.0);

        assertThat(
                loginMetricCount(
                        "success"
                )
        ).isZero();
    }

    @Test
    @DisplayName(
            "비밀번호가 일치하지 않으면 "
                    + "INVALID_CREDENTIALS와 실패 메트릭을 기록한다"
    )
    void login_invalidPassword() {
        // given
        String normalizedEmail =
                "learner@example.com";

        String emailLookupHash =
                "c".repeat(64);

        String passwordHash =
                "argon2-password-hash";

        User user = createUser(
                "encrypted-email",
                emailLookupHash,
                passwordHash
        );

        LoginCommand command =
                new LoginCommand(
                        normalizedEmail,
                        "WrongPassword!"
                );

        when(
                emailNormalizer.normalize(
                        normalizedEmail
                )
        ).thenReturn(
                normalizedEmail
        );

        when(
                emailLookupHasher.hash(
                        normalizedEmail
                )
        ).thenReturn(
                emailLookupHash
        );

        when(
                userRepository
                        .findByEmailLookupHash(
                                emailLookupHash
                        )
        ).thenReturn(
                Optional.of(user)
        );

        when(
                passwordHasher.matches(
                        "WrongPassword!",
                        passwordHash
                )
        ).thenReturn(false);

        // when & then
        assertThatThrownBy(
                () -> loginService.login(command)
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_CREDENTIALS
                );

        verify(passwordHasher)
                .matches(
                        "WrongPassword!",
                        passwordHash
                );

        verify(
                tokenIssuer,
                never()
        ).issueTokens(
                any(),
                any(),
                any()
        );

        verify(
                authSessionStore,
                never()
        ).save(
                any(AuthSession.class)
        );

        assertThat(
                loginMetricCount(
                        "invalid_credentials"
                )
        ).isEqualTo(1.0);

        assertThat(
                loginMetricCount(
                        "success"
                )
        ).isZero();
    }

    @Test
    @DisplayName(
            "비밀번호가 맞더라도 SUSPENDED 사용자면 "
                    + "로그인을 거부하고 비활성 계정 메트릭을 기록한다"
    )
    void login_suspendedUser() {
        // given
        String normalizedEmail =
                "learner@example.com";

        String emailLookupHash =
                "d".repeat(64);

        String passwordHash =
                "argon2-password-hash";

        User user = createUser(
                "encrypted-email",
                emailLookupHash,
                passwordHash
        );

        user.suspend();

        LoginCommand command =
                new LoginCommand(
                        normalizedEmail,
                        "Abcd1234!"
                );

        when(
                emailNormalizer.normalize(
                        normalizedEmail
                )
        ).thenReturn(
                normalizedEmail
        );

        when(
                emailLookupHasher.hash(
                        normalizedEmail
                )
        ).thenReturn(
                emailLookupHash
        );

        when(
                userRepository
                        .findByEmailLookupHash(
                                emailLookupHash
                        )
        ).thenReturn(
                Optional.of(user)
        );

        when(
                passwordHasher.matches(
                        "Abcd1234!",
                        passwordHash
                )
        ).thenReturn(true);

        // when & then
        assertThatThrownBy(
                () -> loginService.login(command)
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_NOT_ACTIVE
                );

        verify(passwordHasher)
                .matches(
                        "Abcd1234!",
                        passwordHash
                );

        verify(
                tokenIssuer,
                never()
        ).issueTokens(
                any(),
                any(),
                any()
        );

        verify(
                authSessionStore,
                never()
        ).save(
                any(AuthSession.class)
        );

        assertThat(
                loginMetricCount(
                        "inactive_user"
                )
        ).isEqualTo(1.0);

        assertThat(
                loginMetricCount(
                        "success"
                )
        ).isZero();

        assertThat(
                loginMetricCount(
                        "invalid_credentials"
                )
        ).isZero();
    }

    /**
     * 지정한 로그인 결과에 해당하는 Counter 값을 반환합니다.
     */
    private double loginMetricCount(
            String result
    ) {
        return meterRegistry
                .find(LOGIN_METRIC_NAME)
                .tag(
                        "result",
                        result
                )
                .counter() == null
                ? 0.0
                : meterRegistry
                .find(LOGIN_METRIC_NAME)
                .tag(
                        "result",
                        result
                )
                .counter()
                .count();
    }

    /**
     * 로그인 테스트에서 사용할 정상 ACTIVE 사용자를 생성합니다.
     */
    private User createUser(
            String encryptedEmail,
            String emailLookupHash,
            String passwordHash
    ) {
        return User.create(
                encryptedEmail,
                emailLookupHash,
                passwordHash,
                "김티암",
                3,
                LearningLevel.BEGINNER
        );
    }
}
