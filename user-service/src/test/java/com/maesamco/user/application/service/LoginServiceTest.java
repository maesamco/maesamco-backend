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
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoginService의 로그인 인증 및 세션 생성 흐름을 검증하는 단위 테스트입니다.
 *
 * <p>이메일 조회, 비밀번호 검증, 사용자 상태 확인,
 * 토큰 발급 및 Redis 인증 세션 저장까지의 흐름을 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private EmailLookupHasher emailLookupHasher;

    @Mock
    private EmailCipher emailCipher;

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

    @InjectMocks
    private LoginService loginService;

    @Test
    @DisplayName("로그인에 성공하면 토큰을 발급하고 Redis 인증 세션을 저장한다")
    void login() {
        // given
        String rawEmail = " Learner@Example.com ";
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);
        String encryptedEmail = "encrypted-email";
        String rawPassword = "Abcd1234!";
        String passwordHash = "argon2-password-hash";
        String refreshTokenHash = "refresh-token-hash";

        User user = createUser(
                encryptedEmail,
                emailLookupHash,
                passwordHash
        );

        Instant now =
                Instant.parse("2026-09-03T01:00:00Z");

        Instant accessTokenExpiresAt =
                now.plusSeconds(900);

        Instant refreshTokenExpiresAt =
                now.plusSeconds(60L * 60 * 24 * 7);

        IssuedTokens issuedTokens = new IssuedTokens(
                "access-token",
                accessTokenExpiresAt,
                "refresh-token",
                refreshTokenExpiresAt
        );

        LoginCommand command = new LoginCommand(
                rawEmail,
                rawPassword
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(userRepository.findByEmailLookupHash(emailLookupHash))
                .thenReturn(Optional.of(user));

        when(
                passwordHasher.matches(
                        rawPassword,
                        passwordHash
                )
        ).thenReturn(true);

        when(emailCipher.decrypt(encryptedEmail))
                .thenReturn(normalizedEmail);

        when(
                tokenIssuer.issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(issuedTokens);

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn(refreshTokenHash);

        when(clock.instant())
                .thenReturn(now);

        // when
        LoginResult result =
                loginService.login(command);

        // then
        verify(emailNormalizer)
                .normalize(trimmedEmail);

        verify(emailLookupHasher)
                .hash(normalizedEmail);

        verify(userRepository)
                .findByEmailLookupHash(emailLookupHash);

        verify(passwordHasher)
                .matches(
                        rawPassword,
                        passwordHash
                );

        verify(emailCipher)
                .decrypt(encryptedEmail);

        ArgumentCaptor<UUID> sessionIdCaptor =
                ArgumentCaptor.forClass(UUID.class);

        verify(tokenIssuer)
                .issueTokens(
                        eq(user.getId()),
                        eq(UserRole.USER),
                        sessionIdCaptor.capture()
                );

        ArgumentCaptor<AuthSession> authSessionCaptor =
                ArgumentCaptor.forClass(AuthSession.class);

        verify(authSessionStore)
                .save(authSessionCaptor.capture());

        AuthSession authSession =
                authSessionCaptor.getValue();

        assertThat(authSession.sessionId())
                .isEqualTo(sessionIdCaptor.getValue());

        assertThat(authSession.familyId())
                .isNotNull();

        assertThat(authSession.userId())
                .isEqualTo(user.getId());

        assertThat(authSession.refreshTokenHash())
                .isEqualTo(refreshTokenHash);

        assertThat(authSession.createdAt())
                .isEqualTo(now);

        assertThat(authSession.expiresAt())
                .isEqualTo(refreshTokenExpiresAt);

        assertThat(result.user().userId())
                .isEqualTo(user.getId());

        assertThat(result.user().email())
                .isEqualTo(normalizedEmail);

        assertThat(result.user().nickname())
                .isEqualTo("김티암");

        assertThat(result.user().role())
                .isEqualTo(UserRole.USER);

        assertThat(result.user().status())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(result.user().learningLevel())
                .isEqualTo(LearningLevel.BEGINNER);

        assertThat(result.accessToken())
                .isEqualTo("access-token");

        assertThat(result.accessTokenExpiresIn())
                .isEqualTo(900);

        assertThat(result.issuedTokens())
                .isSameAs(issuedTokens);
    }

    @Test
    @DisplayName("이메일에 해당하는 사용자가 없으면 INVALID_CREDENTIALS를 반환한다")
    void login_userNotFound() {
        // given
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "b".repeat(64);

        LoginCommand command = new LoginCommand(
                " Learner@Example.com ",
                "Abcd1234!"
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(userRepository.findByEmailLookupHash(emailLookupHash))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(
                passwordHasher,
                never()
        ).matches(any(), any());

        verify(
                emailCipher,
                never()
        ).decrypt(any());

        verify(
                tokenIssuer,
                never()
        ).issueTokens(any(), any(), any());

        verify(
                authSessionStore,
                never()
        ).save(any(AuthSession.class));
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 INVALID_CREDENTIALS를 반환한다")
    void login_invalidPassword() {
        // given
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "c".repeat(64);
        String passwordHash = "argon2-password-hash";

        User user = createUser(
                "encrypted-email",
                emailLookupHash,
                passwordHash
        );

        LoginCommand command = new LoginCommand(
                normalizedEmail,
                "WrongPassword!"
        );

        when(emailNormalizer.normalize(normalizedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(userRepository.findByEmailLookupHash(emailLookupHash))
                .thenReturn(Optional.of(user));

        when(
                passwordHasher.matches(
                        "WrongPassword!",
                        passwordHash
                )
        ).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(
                emailCipher,
                never()
        ).decrypt(any());

        verify(
                tokenIssuer,
                never()
        ).issueTokens(any(), any(), any());

        verify(
                authSessionStore,
                never()
        ).save(any(AuthSession.class));
    }

    @Test
    @DisplayName("비밀번호가 맞더라도 SUSPENDED 사용자면 로그인을 거부한다")
    void login_suspendedUser() {
        // given
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "d".repeat(64);
        String passwordHash = "argon2-password-hash";

        User user = createUser(
                "encrypted-email",
                emailLookupHash,
                passwordHash
        );

        user.suspend();

        LoginCommand command = new LoginCommand(
                normalizedEmail,
                "Abcd1234!"
        );

        when(emailNormalizer.normalize(normalizedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(userRepository.findByEmailLookupHash(emailLookupHash))
                .thenReturn(Optional.of(user));

        when(
                passwordHasher.matches(
                        "Abcd1234!",
                        passwordHash
                )
        ).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(passwordHasher)
                .matches(
                        "Abcd1234!",
                        passwordHash
                );

        verify(
                emailCipher,
                never()
        ).decrypt(any());

        verify(
                tokenIssuer,
                never()
        ).issueTokens(any(), any(), any());

        verify(
                authSessionStore,
                never()
        ).save(any(AuthSession.class));
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
