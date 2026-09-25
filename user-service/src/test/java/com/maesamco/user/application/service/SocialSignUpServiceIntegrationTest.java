package com.maesamco.user.application.service;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionStore;
import com.maesamco.user.application.port.ConsumedSocialSignupToken;
import com.maesamco.user.application.port.EmailLookupHasher;
import com.maesamco.user.application.port.EmailVerificationSecretHasher;
import com.maesamco.user.application.port.IssuedTokens;
import com.maesamco.user.application.port.RefreshTokenHasher;
import com.maesamco.user.application.port.SocialIdentityVerifier;
import com.maesamco.user.application.port.SocialSignupTicket;
import com.maesamco.user.application.port.SocialSignupTokenIssuer;
import com.maesamco.user.application.port.SocialSignupTokenStore;
import com.maesamco.user.application.port.TokenIssuer;
import com.maesamco.user.application.port.VerifiedSocialIdentity;
import com.maesamco.user.domain.entity.LearningLevel;
import com.maesamco.user.domain.entity.SocialAccount;
import com.maesamco.user.domain.entity.SocialProvider;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.repository.SocialAccountRepository;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.config.JpaAuditingConfig;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.infrastructure.persistence.SocialAccountRepositoryImpl;
import com.maesamco.user.infrastructure.persistence.UserGamificationStateRepositoryImpl;
import com.maesamco.user.infrastructure.persistence.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
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
 * 소셜 신규 회원가입 완료 흐름을 실제 PostgreSQL로 검증합니다(#308).
 *
 * <p>User·게이미피케이션 상태·SocialAccount가 하나의 트랜잭션으로 저장되는지,
 * 동일 Google 계정 중복 연결 시 User까지 롤백되는지,
 * 가입을 마친 사용자가 이후 Google 로그인에서 기존 로그인 플로우(AUTHENTICATED)를 타는지 확인합니다.</p>
 *
 * <p>Redis 저장소 동작은 {@code RedisSocialSignupTokenStoreIntegrationTest}에서 별도로 검증하므로
 * 여기서는 Token 저장소와 인증 세션 저장소를 Mock으로 사용합니다.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        UserRepositoryImpl.class,
        UserGamificationStateRepositoryImpl.class,
        SocialAccountRepositoryImpl.class,
        SignUpPersistenceService.class,
        SocialSignUpPersistenceService.class,
        AuthSessionIssuer.class,
        SocialSignUpService.class,
        SocialLoginService.class,
        SocialSignUpServiceIntegrationTest.FakeGoogleVerifierConfig.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SocialSignUpServiceIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-09-24T00:00:00Z");

    private static final String RAW_TOKEN = "social-signup-token";
    private static final String TOKEN_HASH = "h".repeat(64);
    private static final String ENCRYPTED_EMAIL = "encrypted-email";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    /**
     * Google ID Token 검증을 대신합니다.
     * credential 값을 그대로 Google sub로 사용하는 테스트 전용 Verifier입니다.
     */
    @TestConfiguration
    static class FakeGoogleVerifierConfig {

        @Bean
        SocialIdentityVerifier fakeGoogleVerifier() {
            return new SocialIdentityVerifier() {
                @Override
                public SocialProvider provider() {
                    return SocialProvider.GOOGLE;
                }

                @Override
                public VerifiedSocialIdentity verify(String credential) {
                    return new VerifiedSocialIdentity(
                            SocialProvider.GOOGLE,
                            credential,
                            "google-user@example.com",
                            true
                    );
                }
            };
        }
    }

    @Autowired
    private SocialSignUpService socialSignUpService;

    @Autowired
    private SocialSignUpPersistenceService socialSignUpPersistenceService;

    @Autowired
    private SocialLoginService socialLoginService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGamificationStateRepository gamificationStateRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailVerificationSecretHasher secretHasher;

    @MockitoBean
    private SocialSignupTokenStore socialSignupTokenStore;

    @MockitoBean
    private SocialSignupTokenIssuer socialSignupTokenIssuer;

    @MockitoBean
    private EmailNormalizer emailNormalizer;

    @MockitoBean
    private EmailLookupHasher emailLookupHasher;

    @MockitoBean
    private TokenIssuer tokenIssuer;

    @MockitoBean
    private RefreshTokenHasher refreshTokenHasher;

    @MockitoBean
    private AuthSessionStore authSessionStore;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);

        when(
                tokenIssuer.issueTokens(
                        any(UUID.class),
                        eq(UserRole.USER),
                        any(UUID.class)
                )
        ).thenReturn(
                new IssuedTokens(
                        "access-token",
                        NOW.plusSeconds(900),
                        "refresh-token",
                        NOW.plusSeconds(60L * 60 * 24 * 14)
                )
        );

        when(refreshTokenHasher.hash("refresh-token"))
                .thenReturn("refresh-token-hash");
    }

    @Test
    @DisplayName(
            "가입 완료 시 비밀번호 없는 User, 게이미피케이션 상태, Google SocialAccount가 함께 저장되고 "
                    + "이후 Google 로그인은 기존 로그인 플로우(AUTHENTICATED)를 탄다"
    )
    void signUp_thenGoogleLogin_authenticates() {
        // given
        String googleSub = "google-sub-success";
        String emailLookupHash = "a".repeat(64);

        givenTicket(googleSub, emailLookupHash);

        // when
        SignUpResult result =
                socialSignUpService.signUp(
                        command("소셜가입성공")
                );

        // then — User
        User savedUser =
                userRepository.findById(result.userId()).orElseThrow();

        assertThat(savedUser.getEncryptedEmail()).isEqualTo(ENCRYPTED_EMAIL);
        assertThat(savedUser.getEmailLookupHash()).isEqualTo(emailLookupHash);
        assertThat(savedUser.getPasswordHash()).isNull();
        assertThat(savedUser.hasPassword()).isFalse();

        // then — 게이미피케이션 상태
        assertThat(gamificationStateRepository.findByUserId(result.userId())).isPresent();

        // then — User와 SocialAccount 연결
        SocialAccount socialAccount =
                socialAccountRepository
                        .findByProviderAndProviderUserId(SocialProvider.GOOGLE, googleSub)
                        .orElseThrow();

        assertThat(socialAccount.getUserId()).isEqualTo(result.userId());

        verify(authSessionStore).save(any(AuthSession.class));

        // then — 가입 완료 사용자는 이후 기존 로그인 플로우를 사용한다.
        SocialLoginResult loginResult =
                socialLoginService.login(
                        new SocialLoginCommand(SocialProvider.GOOGLE, googleSub)
                );

        assertThat(loginResult.status()).isEqualTo(SocialLoginStatus.AUTHENTICATED);
        assertThat(loginResult.userId()).isEqualTo(result.userId());
        verify(socialSignupTokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName(
            "동일 Google 계정이 이미 연결돼 있으면 SocialAccount 저장 실패와 함께 새 User도 롤백된다"
    )
    void saveSocialUser_duplicateGoogleAccount_rollsBackUser() {
        // given — 다른 요청이 같은 Google 계정으로 먼저 가입을 완료했다.
        String googleSub = "google-sub-race";

        socialSignUpPersistenceService.saveSocialUser(
                User.createSocial(ENCRYPTED_EMAIL, "b".repeat(64), "먼저가입", 1, LearningLevel.BEGINNER),
                SocialProvider.GOOGLE,
                googleSub
        );

        User lateUser =
                User.createSocial(ENCRYPTED_EMAIL, "c".repeat(64), "늦은가입", 1, LearningLevel.BEGINNER);

        // when & then
        assertThatThrownBy(
                () -> socialSignUpPersistenceService.saveSocialUser(
                        lateUser,
                        SocialProvider.GOOGLE,
                        googleSub
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);

        // SocialAccount 없는 소셜 User나 게이미피케이션 상태가 남지 않는다.
        assertThat(userRepository.findById(lateUser.getId())).isEmpty();
        assertThat(gamificationStateRepository.findByUserId(lateUser.getId())).isEmpty();
        assertThat(countUsersByLookupHash("c".repeat(64))).isZero();
    }

    @Test
    @DisplayName("이미 연결된 Google 계정의 Token으로 가입하면 Token을 소비하지 않고 409를 반환한다")
    void signUp_alreadyLinked_doesNotConsumeToken() {
        // given
        String googleSub = "google-sub-already-linked";

        socialSignUpPersistenceService.saveSocialUser(
                User.createSocial(ENCRYPTED_EMAIL, "d".repeat(64), "기존소셜", 1, LearningLevel.BEGINNER),
                SocialProvider.GOOGLE,
                googleSub
        );

        givenTicket(googleSub, "e".repeat(64));

        // when & then
        assertThatThrownBy(() -> socialSignUpService.signUp(command("중복소셜")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);

        verify(socialSignupTokenStore, never()).consume(anyString());
        assertThat(countUsersByLookupHash("e".repeat(64))).isZero();
    }

    @Test
    @DisplayName("Token 발급 후 같은 이메일로 일반 회원가입이 완료됐다면 자동 연결하지 않고 409를 반환한다")
    void signUp_emailTakenByLocalUser() {
        // given
        String emailLookupHash = "f".repeat(64);

        userRepository.save(
                User.create(ENCRYPTED_EMAIL, emailLookupHash, "argon2-hash", "일반회원", 1, LearningLevel.BEGINNER)
        );

        givenTicket("google-sub-email-taken", emailLookupHash);

        // when & then
        assertThatThrownBy(() -> socialSignUpService.signUp(command("이메일충돌")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_SIGNUP_EMAIL_ALREADY_EXISTS);

        verify(socialSignupTokenStore, never()).consume(anyString());
        assertThat(
                socialAccountRepository.findByProviderAndProviderUserId(
                        SocialProvider.GOOGLE,
                        "google-sub-email-taken"
                )
        ).isEmpty();
    }

    private void givenTicket(String googleSub, String emailLookupHash) {
        SocialSignupTicket ticket =
                new SocialSignupTicket(
                        SocialProvider.GOOGLE,
                        googleSub,
                        emailLookupHash,
                        ENCRYPTED_EMAIL
                );

        when(secretHasher.hashSignupToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(socialSignupTokenStore.find(TOKEN_HASH)).thenReturn(Optional.of(ticket));
        when(socialSignupTokenStore.consume(TOKEN_HASH))
                .thenReturn(Optional.of(new ConsumedSocialSignupToken(ticket, Duration.ofMinutes(5))));
    }

    private SocialSignUpCommand command(String nickname) {
        return new SocialSignUpCommand(
                SocialProvider.GOOGLE,
                RAW_TOKEN,
                nickname,
                3,
                LearningLevel.BEGINNER
        );
    }

    private int countUsersByLookupHash(String emailLookupHash) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_schema.p_users WHERE email_lookup_hash = ?",
                        Integer.class,
                        emailLookupHash
                );

        return count == null ? 0 : count;
    }
}
