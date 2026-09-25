package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.SocialIdentityVerifier;
import com.maesamco.user.application.port.SocialSignupTokenIssuer;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * SocialLoginService의 소셜 로그인 분기 정책을 검증합니다.
 *
 * <p>핵심 정책:</p>
 *
 * <p>1. provider + providerUserId로 기존 SocialAccount를 먼저 조회합니다.</p>
 *
 * <p>2. 기존 SocialAccount가 존재하면 이메일 중복 검사 없이 로그인합니다.</p>
 *
 * <p>3. SocialAccount가 없는 신규 소셜 인증에 대해서만
 * 이메일 중복 여부를 확인합니다.</p>
 *
 * <p>4. 동일 이메일의 일반 회원이 존재하면
 * 자동 연결 및 소셜 회원가입을 모두 금지합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

    private static final String GOOGLE_CREDENTIAL =
            "google-id-token";

    private static final String GOOGLE_PROVIDER_USER_ID =
            "google-sub-123";

    private static final String RAW_EMAIL =
            " Learner@Example.com ";

    private static final String TRIMMED_EMAIL =
            "Learner@Example.com";

    private static final String NORMALIZED_EMAIL =
            "learner@example.com";

    private static final String EMAIL_LOOKUP_HASH =
            "a".repeat(64);

    private static final String SOCIAL_SIGNUP_TOKEN =
            "social-signup-token";

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-23T12:00:00Z"
            );

    private static final Instant ACCESS_TOKEN_EXPIRES_AT =
            NOW.plusSeconds(900);

    private static final Instant REFRESH_TOKEN_EXPIRES_AT =
            NOW.plusSeconds(
                    60L * 60 * 24 * 7
            );

    @Mock
    private SocialIdentityVerifier googleIdentityVerifier;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private EmailLookupHasher emailLookupHasher;

    @Mock
    private SocialSignupTokenIssuer socialSignupTokenIssuer;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private Clock clock;

    private SocialLoginService socialLoginService;

    @BeforeEach
    void setUp() {
        when(
                googleIdentityVerifier.provider()
        ).thenReturn(
                SocialProvider.GOOGLE
        );

        socialLoginService =
                new SocialLoginService(
                        List.of(
                                googleIdentityVerifier
                        ),
                        socialAccountRepository,
                        userRepository,
                        emailNormalizer,
                        emailLookupHasher,
                        socialSignupTokenIssuer,
                        new AuthSessionIssuer(
                                tokenIssuer,
                                refreshTokenHasher,
                                authSessionStore,
                                clock
                        )
                );
    }

    @Test
    @DisplayName(
            "기존 Google 소셜 회원은 이메일 중복 검사 없이 로그인한다"
    )
    void login_existingSocialAccount() {
        // given
        User user =
                createUser(
                        EMAIL_LOOKUP_HASH
                );

        SocialAccount socialAccount =
                SocialAccount.create(
                        user.getId(),
                        SocialProvider.GOOGLE,
                        GOOGLE_PROVIDER_USER_ID
                );

        VerifiedSocialIdentity identity =
                createVerifiedGoogleIdentity(
                        RAW_EMAIL,
                        true
                );

        IssuedTokens issuedTokens =
                createIssuedTokens();

        when(
                googleIdentityVerifier.verify(
                        GOOGLE_CREDENTIAL
                )
        ).thenReturn(
                identity
        );

        when(
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE,
                                GOOGLE_PROVIDER_USER_ID
                        )
        ).thenReturn(
                Optional.of(
                        socialAccount
                )
        );

        when(
                userRepository.findByIdForUpdate(
                        user.getId()
                )
        ).thenReturn(
                Optional.of(user)
        );

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
                "refresh-token-hash"
        );

        when(clock.instant())
                .thenReturn(NOW);

        SocialLoginCommand command =
                new SocialLoginCommand(
                        SocialProvider.GOOGLE,
                        GOOGLE_CREDENTIAL
                );

        // when
        SocialLoginResult result =
                socialLoginService.login(
                        command
                );

        // then
        assertThat(result.status())
                .isEqualTo(
                        SocialLoginStatus.AUTHENTICATED
                );

        assertThat(result.provider())
                .isEqualTo(
                        SocialProvider.GOOGLE
                );

        assertThat(result.userId())
                .isEqualTo(
                        user.getId()
                );

        assertThat(result.nickname())
                .isEqualTo(
                        "김티암"
                );

        assertThat(result.role())
                .isEqualTo(
                        UserRole.USER
                );

        assertThat(result.userStatus())
                .isEqualTo(
                        UserStatus.ACTIVE
                );

        assertThat(result.accessToken())
                .isEqualTo(
                        "access-token"
                );

        assertThat(result.accessTokenExpiresIn())
                .isEqualTo(900L);

        assertThat(result.socialSignupToken())
                .isNull();

        assertThat(result.email())
                .isNull();

        /*
         * 기존 소셜 회원은 이미 SocialAccount로 식별됐으므로
         * 이메일을 이용한 신규 가입 충돌 검사를 수행하면 안 됩니다.
         */
        verify(
                emailNormalizer,
                never()
        ).normalize(any());

        verify(
                emailLookupHasher,
                never()
        ).hash(any());

        verify(
                userRepository,
                never()
        ).existsByEmailLookupHash(any());

        verify(
                socialSignupTokenIssuer,
                never()
        ).issue(any());

        verify(
                socialAccountRepository,
                never()
        ).save(any());

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
                authSession.userId()
        ).isEqualTo(
                user.getId()
        );

        assertThat(
                authSession.refreshTokenHash()
        ).isEqualTo(
                "refresh-token-hash"
        );

        assertThat(
                authSession.createdAt()
        ).isEqualTo(
                NOW
        );

        assertThat(
                authSession.expiresAt()
        ).isEqualTo(
                REFRESH_TOKEN_EXPIRES_AT
        );
    }

    @Test
    @DisplayName(
            "동일 이메일의 일반 회원이 존재하면 소셜 회원가입을 거부한다"
    )
    void login_rejectsExistingLocalEmail() {
        // given
        VerifiedSocialIdentity identity =
                createVerifiedGoogleIdentity(
                        RAW_EMAIL,
                        true
                );

        when(
                googleIdentityVerifier.verify(
                        GOOGLE_CREDENTIAL
                )
        ).thenReturn(
                identity
        );

        when(
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE,
                                GOOGLE_PROVIDER_USER_ID
                        )
        ).thenReturn(
                Optional.empty()
        );

        /*
         * VerifiedSocialIdentity가 생성되면서 앞뒤 공백을 제거하므로
         * EmailNormalizer에는 TRIMMED_EMAIL이 전달됩니다.
         */
        when(
                emailNormalizer.normalize(
                        TRIMMED_EMAIL
                )
        ).thenReturn(
                NORMALIZED_EMAIL
        );

        when(
                emailLookupHasher.hash(
                        NORMALIZED_EMAIL
                )
        ).thenReturn(
                EMAIL_LOOKUP_HASH
        );

        when(
                userRepository.existsByEmailLookupHash(
                        EMAIL_LOOKUP_HASH
                )
        ).thenReturn(true);

        SocialLoginCommand command =
                new SocialLoginCommand(
                        SocialProvider.GOOGLE,
                        GOOGLE_CREDENTIAL
                );

        // when & then
        assertThatThrownBy(
                () -> socialLoginService.login(
                        command
                )
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
                        ErrorCode
                                .SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS
                );

        /*
         * 기존 LOCAL 이메일이 발견되면
         * 신규 가입, 자동 연결, 토큰 발급을 모두 수행하지 않습니다.
         */
        verify(
                socialSignupTokenIssuer,
                never()
        ).issue(any());

        verify(
                socialAccountRepository,
                never()
        ).save(any());

        verify(
                tokenIssuer,
                never()
        ).issueTokens(
                any(),
                any(),
                any()
        );

        verify(
                refreshTokenHasher,
                never()
        ).hash(any());

        verify(
                authSessionStore,
                never()
        ).save(any());
    }

    @Test
    @DisplayName(
            "신규 Google 사용자는 User를 생성하지 않고 추가 회원가입 상태를 반환한다"
    )
    void login_newSocialUserRequiresSignup() {
        // given
        VerifiedSocialIdentity identity =
                createVerifiedGoogleIdentity(
                        RAW_EMAIL,
                        true
                );

        when(
                googleIdentityVerifier.verify(
                        GOOGLE_CREDENTIAL
                )
        ).thenReturn(
                identity
        );

        when(
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE,
                                GOOGLE_PROVIDER_USER_ID
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                emailNormalizer.normalize(
                        TRIMMED_EMAIL
                )
        ).thenReturn(
                NORMALIZED_EMAIL
        );

        when(
                emailLookupHasher.hash(
                        NORMALIZED_EMAIL
                )
        ).thenReturn(
                EMAIL_LOOKUP_HASH
        );

        when(
                userRepository.existsByEmailLookupHash(
                        EMAIL_LOOKUP_HASH
                )
        ).thenReturn(false);

        when(
                socialSignupTokenIssuer.issue(
                        any(VerifiedSocialIdentity.class)
                )
        ).thenReturn(
                SOCIAL_SIGNUP_TOKEN
        );

        SocialLoginCommand command =
                new SocialLoginCommand(
                        SocialProvider.GOOGLE,
                        GOOGLE_CREDENTIAL
                );

        // when
        SocialLoginResult result =
                socialLoginService.login(
                        command
                );

        // then
        assertThat(result.status())
                .isEqualTo(
                        SocialLoginStatus.SIGNUP_REQUIRED
                );

        assertThat(result.provider())
                .isEqualTo(
                        SocialProvider.GOOGLE
                );

        assertThat(result.socialSignupToken())
                .isEqualTo(
                        SOCIAL_SIGNUP_TOKEN
                );

        assertThat(result.email())
                .isEqualTo(
                        NORMALIZED_EMAIL
                );

        assertThat(result.userId())
                .isNull();

        assertThat(result.accessToken())
                .isNull();

        assertThat(result.issuedTokens())
                .isNull();

        /*
         * 소셜 인증만 끝난 시점에는 실제 User와
         * SocialAccount를 생성하지 않습니다.
         */
        verify(
                socialAccountRepository,
                never()
        ).save(any());

        verify(
                tokenIssuer,
                never()
        ).issueTokens(
                any(),
                any(),
                any()
        );

        verify(
                refreshTokenHasher,
                never()
        ).hash(any());

        verify(
                authSessionStore,
                never()
        ).save(any());

        ArgumentCaptor<VerifiedSocialIdentity>
                identityCaptor =
                ArgumentCaptor.forClass(
                        VerifiedSocialIdentity.class
                );

        verify(socialSignupTokenIssuer)
                .issue(
                        identityCaptor.capture()
                );

        VerifiedSocialIdentity issuedIdentity =
                identityCaptor.getValue();

        assertThat(
                issuedIdentity.provider()
        ).isEqualTo(
                SocialProvider.GOOGLE
        );

        assertThat(
                issuedIdentity.email()
        ).isEqualTo(
                NORMALIZED_EMAIL
        );

        assertThat(
                issuedIdentity.providerUserId()
        ).isEqualTo(
                GOOGLE_PROVIDER_USER_ID
        );

        assertThat(
                issuedIdentity.emailVerified()
        ).isTrue();
    }

    @Test
    @DisplayName(
            "소셜 Provider의 이메일 인증이 완료되지 않았으면 로그인을 거부한다"
    )
    void login_rejectsUnverifiedEmail() {
        // given
        VerifiedSocialIdentity identity =
                createVerifiedGoogleIdentity(
                        NORMALIZED_EMAIL,
                        false
                );

        when(
                googleIdentityVerifier.verify(
                        GOOGLE_CREDENTIAL
                )
        ).thenReturn(
                identity
        );

        SocialLoginCommand command =
                new SocialLoginCommand(
                        SocialProvider.GOOGLE,
                        GOOGLE_CREDENTIAL
                );

        // when & then
        assertThatThrownBy(
                () -> socialLoginService.login(
                        command
                )
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
                        ErrorCode.SOCIAL_EMAIL_NOT_VERIFIED
                );

        verify(
                socialAccountRepository,
                never()
        ).findByProviderAndProviderUserId(
                any(),
                any()
        );

        verify(
                userRepository,
                never()
        ).existsByEmailLookupHash(any());

        verify(
                socialSignupTokenIssuer,
                never()
        ).issue(any());

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
        ).save(any());
    }

    @Test
    @DisplayName(
            "Verifier가 요청과 다른 Provider 정보를 반환하면 인증을 거부한다"
    )
    void login_rejectsProviderMismatch() {
        // given
        VerifiedSocialIdentity mismatchedIdentity =
                new VerifiedSocialIdentity(
                        SocialProvider.KAKAO,
                        "kakao-user-id",
                        NORMALIZED_EMAIL,
                        true
                );

        when(
                googleIdentityVerifier.verify(
                        GOOGLE_CREDENTIAL
                )
        ).thenReturn(
                mismatchedIdentity
        );

        SocialLoginCommand command =
                new SocialLoginCommand(
                        SocialProvider.GOOGLE,
                        GOOGLE_CREDENTIAL
                );

        // when & then
        assertThatThrownBy(
                () -> socialLoginService.login(
                        command
                )
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
                        ErrorCode.AUTH_INVALID_TOKEN
                );

        verify(
                socialAccountRepository,
                never()
        ).findByProviderAndProviderUserId(
                any(),
                any()
        );

        verify(
                userRepository,
                never()
        ).existsByEmailLookupHash(any());

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
        ).save(any());
    }

    @Test
    @DisplayName(
            "등록된 Verifier가 없는 Provider 요청은 거부한다"
    )
    void login_rejectsUnsupportedProvider() {
        // given
        SocialLoginCommand command =
                new SocialLoginCommand(
                        SocialProvider.KAKAO,
                        "kakao-token"
                );

        // when & then
        assertThatThrownBy(
                () -> socialLoginService.login(
                        command
                )
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
                        ErrorCode
                                .SOCIAL_PROVIDER_NOT_SUPPORTED
                );

        verify(
                googleIdentityVerifier,
                never()
        ).verify(any());

        verify(
                socialAccountRepository,
                never()
        ).findByProviderAndProviderUserId(
                any(),
                any()
        );

        verify(
                userRepository,
                never()
        ).existsByEmailLookupHash(any());

        verify(
                tokenIssuer,
                never()
        ).issueTokens(
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName(
            "기존 소셜 계정의 사용자가 이용 정지 상태면 로그인을 거부한다"
    )
    void login_rejectsSuspendedSocialUser() {
        // given
        User user =
                createUser(
                        EMAIL_LOOKUP_HASH
                );

        user.suspend();

        SocialAccount socialAccount =
                SocialAccount.create(
                        user.getId(),
                        SocialProvider.GOOGLE,
                        GOOGLE_PROVIDER_USER_ID
                );

        VerifiedSocialIdentity identity =
                createVerifiedGoogleIdentity(
                        NORMALIZED_EMAIL,
                        true
                );

        when(
                googleIdentityVerifier.verify(
                        GOOGLE_CREDENTIAL
                )
        ).thenReturn(
                identity
        );

        when(
                socialAccountRepository
                        .findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE,
                                GOOGLE_PROVIDER_USER_ID
                        )
        ).thenReturn(
                Optional.of(
                        socialAccount
                )
        );

        when(
                userRepository.findByIdForUpdate(
                        user.getId()
                )
        ).thenReturn(
                Optional.of(user)
        );

        SocialLoginCommand command =
                new SocialLoginCommand(
                        SocialProvider.GOOGLE,
                        GOOGLE_CREDENTIAL
                );

        // when & then
        assertThatThrownBy(
                () -> socialLoginService.login(
                        command
                )
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

        verify(
                tokenIssuer,
                never()
        ).issueTokens(
                any(),
                any(),
                any()
        );

        verify(
                refreshTokenHasher,
                never()
        ).hash(any());

        verify(
                authSessionStore,
                never()
        ).save(any());

        /*
         * 기존 SocialAccount로 식별됐으므로
         * SUSPENDED 상태여도 신규 가입 이메일 검사는 하지 않습니다.
         */
        verify(
                emailNormalizer,
                never()
        ).normalize(any());

        verify(
                emailLookupHasher,
                never()
        ).hash(any());

        verify(
                userRepository,
                never()
        ).existsByEmailLookupHash(any());
    }

    /**
     * Google Verifier가 반환할 테스트용 검증 완료 Identity를 생성합니다.
     */
    private VerifiedSocialIdentity
    createVerifiedGoogleIdentity(
            String email,
            boolean emailVerified
    ) {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                GOOGLE_PROVIDER_USER_ID,
                email,
                emailVerified
        );
    }

    /**
     * 기존 Social 사용자 로그인 테스트에 사용할
     * Access/Refresh Token을 생성합니다.
     */
    private IssuedTokens createIssuedTokens() {
        return new IssuedTokens(
                "access-token",
                ACCESS_TOKEN_EXPIRES_AT,
                "refresh-token",
                REFRESH_TOKEN_EXPIRES_AT
        );
    }

    /**
     * SocialAccount에 연결할 테스트용 ACTIVE 사용자를 생성합니다.
     */
    private User createUser(
            String emailLookupHash
    ) {
        return User.create(
                "encrypted-email",
                emailLookupHash,
                "argon2-password-hash",
                "김티암",
                3,
                LearningLevel.BEGINNER
        );
    }
}
