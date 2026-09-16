package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailCipher;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.EmailVerificationStore;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.PasswordHasher;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SignUpService의 회원가입 오케스트레이션을 검증하는 단위 테스트입니다.
 *
 * <p>이메일 인증 토큰의 사전 검증과 일회성 소비,
 * 사용자 저장, 인증 토큰 발급 및 Redis 인증 세션 저장까지의
 * 회원가입 흐름을 검증합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SignUpServiceTest {

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private EmailCipher emailCipher;

    @Mock
    private EmailLookupHasher emailLookupHasher;

    @Mock
    private EmailVerificationSecretHasher emailVerificationSecretHasher;

    @Mock
    private EmailVerificationStore emailVerificationStore;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private SignUpPersistenceService signUpPersistenceService;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private Clock clock;

    @InjectMocks
    private SignUpService signUpService;

    @Test
    @DisplayName(
            "유효한 이메일 인증 토큰으로 회원가입하면 "
                    + "사용자를 저장하고 인증 세션을 생성한다"
    )
    void signUp() {
        // given
        String rawEmail = " Learner@Example.com ";
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);

        String signupToken = "signup-token";
        String signupTokenHash = "c".repeat(64);

        String encryptedEmail = "encrypted-email";
        String rawPassword = "Abcd1234!";
        String passwordHash = "argon2-password-hash";
        String refreshTokenHash = "refresh-token-hash";

        Instant now =
                Instant.parse("2026-09-02T11:00:00Z");

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

        SignUpCommand command = new SignUpCommand(
                rawEmail,
                signupToken,
                rawPassword,
                " 김티암 ",
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(
                emailVerificationSecretHasher.hashSignupToken(
                        signupToken
                )
        ).thenReturn(signupTokenHash);

        when(
                emailVerificationStore.isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        when(
                emailVerificationStore.consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        when(emailCipher.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(passwordHasher.hash(rawPassword))
                .thenReturn(passwordHash);

        when(signUpPersistenceService.saveUser(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(
                tokenIssuer.issueTokens(
                        any(UUID.class),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(issuedTokens);

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn(refreshTokenHash);

        when(clock.instant())
                .thenReturn(now);

        // when
        SignUpResult result =
                signUpService.signUp(command);

        // then
        verify(emailNormalizer)
                .normalize(trimmedEmail);

        verify(emailVerificationSecretHasher)
                .hashSignupToken(signupToken);

        verify(emailVerificationStore)
                .isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                );

        verify(signUpPersistenceService)
                .validateNicknameNotDuplicated("김티암");

        verify(emailVerificationStore)
                .consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(signUpPersistenceService)
                .saveUser(userCaptor.capture());

        User savedUser =
                userCaptor.getValue();

        assertThat(savedUser.getId())
                .isNotNull();

        assertThat(savedUser.getEncryptedEmail())
                .isEqualTo(encryptedEmail);

        assertThat(savedUser.getEmailLookupHash())
                .isEqualTo(emailLookupHash);

        assertThat(savedUser.getPasswordHash())
                .isEqualTo(passwordHash);

        assertThat(savedUser.getNickname())
                .isEqualTo("김티암");

        assertThat(savedUser.getRole())
                .isEqualTo(UserRole.USER);

        assertThat(savedUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(savedUser.getJavaExperienceMonths())
                .isEqualTo(3);

        assertThat(savedUser.getLearningLevel())
                .isEqualTo(LearningLevel.BEGINNER);

        ArgumentCaptor<UUID> sessionIdCaptor =
                ArgumentCaptor.forClass(UUID.class);

        verify(tokenIssuer)
                .issueTokens(
                        eq(savedUser.getId()),
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
                .isEqualTo(savedUser.getId());

        assertThat(authSession.refreshTokenHash())
                .isEqualTo(refreshTokenHash);

        assertThat(authSession.createdAt())
                .isEqualTo(now);

        assertThat(authSession.expiresAt())
                .isEqualTo(refreshTokenExpiresAt);

        assertThat(result.userId())
                .isEqualTo(savedUser.getId());

        assertThat(result.issuedTokens())
                .isSameAs(issuedTokens);
    }

    @Test
    @DisplayName(
            "유효하지 않은 이메일 인증 토큰이면 "
                    + "닉네임 조회와 회원가입 저장을 수행하지 않는다"
    )
    void signUp_invalidVerificationToken() {
        // given
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);

        String signupToken = "invalid-signup-token";
        String signupTokenHash = "c".repeat(64);

        SignUpCommand command = new SignUpCommand(
                trimmedEmail,
                signupToken,
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(
                emailVerificationSecretHasher.hashSignupToken(
                        signupToken
                )
        ).thenReturn(signupTokenHash);

        when(
                emailVerificationStore.isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
                signUpService.signUp(command)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.SIGNUP_VERIFICATION_TOKEN_INVALID
                );

        verify(emailVerificationSecretHasher)
                .hashSignupToken(signupToken);

        verify(emailVerificationStore)
                .isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                );

        /*
         * 이메일 인증을 통과하지 못한 요청은 닉네임 존재 여부를
         * 확인할 수 없어야 합니다.
         */
        verify(
                signUpPersistenceService,
                never()
        ).validateNicknameNotDuplicated(
                any(String.class)
        );

        verify(
                emailVerificationStore,
                never()
        ).consumeSignupToken(
                any(String.class),
                any(String.class)
        );

        verify(
                emailCipher,
                never()
        ).encrypt(any(String.class));

        verify(
                passwordHasher,
                never()
        ).hash(any(String.class));

        verify(
                signUpPersistenceService,
                never()
        ).saveUser(any(User.class));

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
        ).save(any(AuthSession.class));
    }

    @Test
    @DisplayName(
            "유효한 인증 토큰을 소비한 뒤 저장 단계에서 "
                    + "중복 이메일이면 회원가입을 거부한다"
    )
    void signUp_duplicateEmailAtPersistenceStep() {
        // given
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);

        String signupToken = "signup-token";
        String signupTokenHash = "c".repeat(64);

        SignUpCommand command = new SignUpCommand(
                trimmedEmail,
                signupToken,
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(
                emailVerificationSecretHasher.hashSignupToken(
                        signupToken
                )
        ).thenReturn(signupTokenHash);

        when(
                emailVerificationStore.isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        when(
                emailVerificationStore.consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        when(emailCipher.encrypt(normalizedEmail))
                .thenReturn("encrypted-email");

        when(passwordHasher.hash("Abcd1234!"))
                .thenReturn("argon2-password-hash");

        when(
                signUpPersistenceService.saveUser(
                        any(User.class)
                )
        ).thenThrow(
                new BusinessException(
                        ErrorCode.USER_DUPLICATE_EMAIL
                )
        );

        // when & then
        assertThatThrownBy(() ->
                signUpService.signUp(command)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_DUPLICATE_EMAIL
                );

        verify(emailVerificationStore)
                .isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                );

        verify(signUpPersistenceService)
                .validateNicknameNotDuplicated("김티암");

        verify(emailVerificationStore)
                .consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                );

        verify(signUpPersistenceService)
                .saveUser(any(User.class));

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
        ).save(any(AuthSession.class));
    }

    @Test
    @DisplayName(
            "Redis 인증 세션 저장에 실패하면 "
                    + "자동 로그인 실패 예외를 반환한다"
    )
    void signUp_autoLoginFailsWhenAuthSessionSaveFails() {
        // given
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "b".repeat(64);

        String signupToken = "signup-token";
        String signupTokenHash = "c".repeat(64);

        Instant now =
                Instant.parse("2026-09-02T11:00:00Z");

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

        SignUpCommand command = new SignUpCommand(
                " Learner@Example.com ",
                signupToken,
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(
                emailVerificationSecretHasher.hashSignupToken(
                        signupToken
                )
        ).thenReturn(signupTokenHash);

        when(
                emailVerificationStore.isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        when(
                emailVerificationStore.consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        when(emailCipher.encrypt(normalizedEmail))
                .thenReturn("encrypted-email");

        when(passwordHasher.hash("Abcd1234!"))
                .thenReturn("argon2-password-hash");

        when(signUpPersistenceService.saveUser(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(
                tokenIssuer.issueTokens(
                        any(UUID.class),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(issuedTokens);

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(clock.instant())
                .thenReturn(now);

        doThrow(
                new IllegalStateException(
                        "Redis 인증 세션 저장 실패"
                )
        ).when(authSessionStore)
                .save(any(AuthSession.class));

        // when & then
        assertThatThrownBy(() ->
                signUpService.signUp(command)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.SIGNUP_AUTO_LOGIN_FAILED
                );

        verify(emailVerificationStore)
                .isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                );

        verify(emailVerificationStore)
                .consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                );

        verify(signUpPersistenceService)
                .saveUser(any(User.class));

        verify(authSessionStore)
                .save(any(AuthSession.class));

        verify(
                authSessionStore,
                never()
        ).deleteBySessionId(
                any(UUID.class)
        );
    }

    @Test
    @DisplayName(
            "유효한 인증 토큰을 확인한 뒤 닉네임이 중복이면 "
                    + "토큰을 소비하지 않고 회원가입을 거부한다"
    )
    void signUp_duplicateNicknameAfterVerificationValidation_doesNotConsumeToken() {
        // given
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);

        String signupToken = "signup-token";
        String signupTokenHash = "c".repeat(64);
        String normalizedNickname = "김티암";

        SignUpCommand command = new SignUpCommand(
                trimmedEmail,
                signupToken,
                "Abcd1234!",
                normalizedNickname,
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(
                emailVerificationSecretHasher.hashSignupToken(
                        signupToken
                )
        ).thenReturn(signupTokenHash);

        when(
                emailVerificationStore.isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        doThrow(
                new BusinessException(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                )
        )
                .when(signUpPersistenceService)
                .validateNicknameNotDuplicated(
                        normalizedNickname
                );

        // when & then
        assertThatThrownBy(() ->
                signUpService.signUp(command)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                );

        verify(emailVerificationSecretHasher)
                .hashSignupToken(signupToken);

        verify(emailVerificationStore)
                .isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                );

        verify(signUpPersistenceService)
                .validateNicknameNotDuplicated(
                        normalizedNickname
                );

        /*
         * 닉네임 중복은 사전 검증 이후 발견되므로
         * signup token은 아직 소비되지 않아야 합니다.
         */
        verify(
                emailVerificationStore,
                never()
        ).consumeSignupToken(
                any(String.class),
                any(String.class)
        );

        verify(
                emailCipher,
                never()
        ).encrypt(any(String.class));

        verify(
                passwordHasher,
                never()
        ).hash(any(String.class));

        verify(
                signUpPersistenceService,
                never()
        ).saveUser(any(User.class));

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
        ).save(any(AuthSession.class));
    }

    @Test
    @DisplayName(
            "인증 토큰 소비 후 저장 단계에서 닉네임 중복이 발생하면 "
                    + "중복 닉네임 예외를 반환한다"
    )
    void signUp_duplicateNicknameAtPersistenceStep_afterTokenConsumption() {
        // given
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);

        String signupToken = "signup-token";
        String signupTokenHash = "c".repeat(64);
        String normalizedNickname = "김티암";

        SignUpCommand command = new SignUpCommand(
                trimmedEmail,
                signupToken,
                "Abcd1234!",
                normalizedNickname,
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(
                emailVerificationSecretHasher.hashSignupToken(
                        signupToken
                )
        ).thenReturn(signupTokenHash);

        when(
                emailVerificationStore.isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        when(
                emailVerificationStore.consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        when(emailCipher.encrypt(normalizedEmail))
                .thenReturn("encrypted-email");

        when(passwordHasher.hash("Abcd1234!"))
                .thenReturn("argon2-password-hash");

        /*
         * 사전 닉네임 중복 검사를 통과한 직후
         * 다른 요청이 같은 닉네임을 먼저 저장한 상황을 재현합니다.
         */
        when(
                signUpPersistenceService.saveUser(
                        any(User.class)
                )
        ).thenThrow(
                new BusinessException(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                )
        );

        // when & then
        assertThatThrownBy(() ->
                signUpService.signUp(command)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                );

        /*
         * 보안 흐름의 호출 순서를 검증합니다.
         *
         * 1. signup token 사전 검증
         * 2. 닉네임 중복 검사
         * 3. signup token 최종 소비
         * 4. 사용자 저장
         */
        InOrder inOrder = inOrder(
                emailVerificationStore,
                signUpPersistenceService
        );

        inOrder.verify(emailVerificationStore)
                .isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                );

        inOrder.verify(signUpPersistenceService)
                .validateNicknameNotDuplicated(
                        normalizedNickname
                );

        inOrder.verify(emailVerificationStore)
                .consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                );

        inOrder.verify(signUpPersistenceService)
                .saveUser(any(User.class));

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
        ).save(any(AuthSession.class));
    }

    @Test
    @DisplayName(
            "사전 검증 후 최종 소비 전에 인증 토큰이 사용되면 "
                    + "회원가입을 거부한다"
    )
    void signUp_verificationTokenConsumedByConcurrentRequest() {
        // given
        String trimmedEmail = "Learner@Example.com";
        String normalizedEmail = "learner@example.com";
        String emailLookupHash = "a".repeat(64);

        String signupToken = "signup-token";
        String signupTokenHash = "c".repeat(64);

        SignUpCommand command = new SignUpCommand(
                trimmedEmail,
                signupToken,
                "Abcd1234!",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );

        when(emailNormalizer.normalize(trimmedEmail))
                .thenReturn(normalizedEmail);

        when(emailLookupHasher.hash(normalizedEmail))
                .thenReturn(emailLookupHash);

        when(
                emailVerificationSecretHasher.hashSignupToken(
                        signupToken
                )
        ).thenReturn(signupTokenHash);

        /*
         * 최초 조회 시점에는 토큰이 유효합니다.
         */
        when(
                emailVerificationStore.isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(true);

        /*
         * 사전 검증 이후 다른 요청이 먼저 토큰을 소비한 상황을
         * 재현합니다.
         */
        when(
                emailVerificationStore.consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                )
        ).thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
                signUpService.signUp(command)
        )
                .isInstanceOf(BusinessException.class)
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.SIGNUP_VERIFICATION_TOKEN_INVALID
                );

        InOrder inOrder = inOrder(
                emailVerificationStore,
                signUpPersistenceService
        );

        inOrder.verify(emailVerificationStore)
                .isSignupTokenValid(
                        signupTokenHash,
                        emailLookupHash
                );

        inOrder.verify(signUpPersistenceService)
                .validateNicknameNotDuplicated(
                        "김티암"
                );

        inOrder.verify(emailVerificationStore)
                .consumeSignupToken(
                        signupTokenHash,
                        emailLookupHash
                );

        /*
         * 최종 원자 소비에 실패했으므로
         * 실제 회원 생성 작업으로 진입하면 안 됩니다.
         */
        verify(
                emailCipher,
                never()
        ).encrypt(any(String.class));

        verify(
                passwordHasher,
                never()
        ).hash(any(String.class));

        verify(
                signUpPersistenceService,
                never()
        ).saveUser(any(User.class));

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
        ).save(any(AuthSession.class));
    }
}
