package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * RedisEmailVerificationStore의 Redis 호출과
 * 이메일 인증 결과 매핑 규칙을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RedisEmailVerificationStoreTest {

    private static final String EMAIL_LOOKUP_HASH =
            "email-lookup-hash";

    private static final String VERIFICATION_CODE_HASH =
            "verification-code-hash";

    private static final String SIGNUP_TOKEN_HASH =
            "signup-token-hash";

    private static final Duration CHALLENGE_TTL =
            Duration.ofMinutes(10);

    private static final Duration RESEND_COOLDOWN =
            Duration.ofMinutes(1);

    private static final Duration REQUEST_LIMIT_WINDOW =
            Duration.ofHours(1);

    private static final Duration SIGNUP_TOKEN_TTL =
            Duration.ofMinutes(10);

    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;

    private static final String EMAIL_KEY_PREFIX =
            "email-verification:{"
                    + EMAIL_LOOKUP_HASH
                    + "}";

    private static final String CHALLENGE_KEY =
            EMAIL_KEY_PREFIX + ":challenge";

    private static final String COOLDOWN_KEY =
            EMAIL_KEY_PREFIX + ":cooldown";

    private static final String REQUEST_COUNT_KEY =
            EMAIL_KEY_PREFIX + ":request-count";

    private static final String SIGNUP_TOKEN_KEY =
            EMAIL_KEY_PREFIX
                    + ":signup-token:"
                    + SIGNUP_TOKEN_HASH;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmailVerificationStore emailVerificationStore;

    @BeforeEach
    void setUp() {
        emailVerificationStore =
                new RedisEmailVerificationStore(
                        redisTemplate
                );
    }

    @Test
    @DisplayName("이메일 인증 challenge 생성에 성공하면 CREATED를 반환한다")
    void createChallenge_returnsCreated() {
        // given
        prepareChallengeCreationResult(1L);

        // when
        EmailVerificationStore.ChallengeCreationResult result =
                emailVerificationStore.createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        CHALLENGE_TTL,
                        RESEND_COOLDOWN,
                        REQUEST_LIMIT_WINDOW,
                        MAX_REQUESTS_PER_WINDOW
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ChallengeCreationResult
                                .CREATED
                );
    }

    @Test
    @DisplayName("재전송 제한 중이면 COOLDOWN_ACTIVE를 반환한다")
    void createChallenge_returnsCooldownActive() {
        // given
        prepareChallengeCreationResult(2L);

        // when
        EmailVerificationStore.ChallengeCreationResult result =
                emailVerificationStore.createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        CHALLENGE_TTL,
                        RESEND_COOLDOWN,
                        REQUEST_LIMIT_WINDOW,
                        MAX_REQUESTS_PER_WINDOW
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ChallengeCreationResult
                                .COOLDOWN_ACTIVE
                );
    }

    @Test
    @DisplayName("이메일 인증 요청 횟수 제한을 초과하면 RATE_LIMIT_EXCEEDED를 반환한다")
    void createChallenge_returnsRateLimitExceeded() {
        // given
        prepareChallengeCreationResult(3L);

        // when
        EmailVerificationStore.ChallengeCreationResult result =
                emailVerificationStore.createChallenge(
                        EMAIL_LOOKUP_HASH,
                        VERIFICATION_CODE_HASH,
                        CHALLENGE_TTL,
                        RESEND_COOLDOWN,
                        REQUEST_LIMIT_WINDOW,
                        MAX_REQUESTS_PER_WINDOW
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ChallengeCreationResult
                                .RATE_LIMIT_EXCEEDED
                );
    }

    @Test
    @DisplayName("이메일 인증에 성공하면 VERIFIED를 반환한다")
    void confirmAndIssueSignupToken_returnsVerified() {
        // given
        prepareConfirmationResult(1L);

        // when
        EmailVerificationStore.ConfirmationResult result =
                emailVerificationStore
                        .confirmAndIssueSignupToken(
                                EMAIL_LOOKUP_HASH,
                                VERIFICATION_CODE_HASH,
                                SIGNUP_TOKEN_HASH,
                                SIGNUP_TOKEN_TTL,
                                MAX_VERIFICATION_ATTEMPTS
                        );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ConfirmationResult
                                .VERIFIED
                );
    }

    @Test
    @DisplayName("인증 코드가 일치하지 않으면 INVALID_CODE를 반환한다")
    void confirmAndIssueSignupToken_returnsInvalidCode() {
        // given
        prepareConfirmationResult(2L);

        // when
        EmailVerificationStore.ConfirmationResult result =
                emailVerificationStore
                        .confirmAndIssueSignupToken(
                                EMAIL_LOOKUP_HASH,
                                VERIFICATION_CODE_HASH,
                                SIGNUP_TOKEN_HASH,
                                SIGNUP_TOKEN_TTL,
                                MAX_VERIFICATION_ATTEMPTS
                        );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ConfirmationResult
                                .INVALID_CODE
                );
    }

    @Test
    @DisplayName("인증 challenge가 없거나 만료되었으면 EXPIRED를 반환한다")
    void confirmAndIssueSignupToken_returnsExpired() {
        // given
        prepareConfirmationResult(3L);

        // when
        EmailVerificationStore.ConfirmationResult result =
                emailVerificationStore
                        .confirmAndIssueSignupToken(
                                EMAIL_LOOKUP_HASH,
                                VERIFICATION_CODE_HASH,
                                SIGNUP_TOKEN_HASH,
                                SIGNUP_TOKEN_TTL,
                                MAX_VERIFICATION_ATTEMPTS
                        );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ConfirmationResult
                                .EXPIRED
                );
    }

    @Test
    @DisplayName("최대 인증 실패 횟수에 도달하면 ATTEMPTS_EXCEEDED를 반환한다")
    void confirmAndIssueSignupToken_returnsAttemptsExceeded() {
        // given
        prepareConfirmationResult(4L);

        // when
        EmailVerificationStore.ConfirmationResult result =
                emailVerificationStore
                        .confirmAndIssueSignupToken(
                                EMAIL_LOOKUP_HASH,
                                VERIFICATION_CODE_HASH,
                                SIGNUP_TOKEN_HASH,
                                SIGNUP_TOKEN_TTL,
                                MAX_VERIFICATION_ATTEMPTS
                        );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ConfirmationResult
                                .ATTEMPTS_EXCEEDED
                );
    }

    @Test
    @DisplayName("회원가입 인증 토큰 소비에 성공하면 true를 반환한다")
    void consumeSignupToken_returnsTrueWhenConsumed() {
        // given
        prepareTokenConsumptionResult(1L);

        // when
        boolean result =
                emailVerificationStore.consumeSignupToken(
                        SIGNUP_TOKEN_HASH,
                        EMAIL_LOOKUP_HASH
                );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("회원가입 인증 토큰이 이메일에 정상 귀속되어 있으면 true를 반환한다")
    void isSignupTokenValid_returnsTrueWhenTokenMatchesEmail() {
        // given
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(
                valueOperations.get(
                        SIGNUP_TOKEN_KEY
                )
        ).thenReturn(EMAIL_LOOKUP_HASH);

        // when
        boolean result =
                emailVerificationStore.isSignupTokenValid(
                        SIGNUP_TOKEN_HASH,
                        EMAIL_LOOKUP_HASH
                );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("회원가입 인증 토큰이 존재하지 않으면 false를 반환한다")
    void isSignupTokenValid_returnsFalseWhenTokenDoesNotExist() {
        // given
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(
                valueOperations.get(
                        SIGNUP_TOKEN_KEY
                )
        ).thenReturn(null);

        // when
        boolean result =
                emailVerificationStore.isSignupTokenValid(
                        SIGNUP_TOKEN_HASH,
                        EMAIL_LOOKUP_HASH
                );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("회원가입 인증 토큰의 이메일 귀속 정보가 다르면 false를 반환한다")
    void isSignupTokenValid_returnsFalseWhenEmailDoesNotMatch() {
        // given
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(
                valueOperations.get(
                        SIGNUP_TOKEN_KEY
                )
        ).thenReturn("different-email-lookup-hash");

        // when
        boolean result =
                emailVerificationStore.isSignupTokenValid(
                        SIGNUP_TOKEN_HASH,
                        EMAIL_LOOKUP_HASH
                );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("회원가입 인증 토큰을 소비할 수 없으면 false를 반환한다")
    void consumeSignupToken_returnsFalseWhenNotConsumed() {
        // given
        prepareTokenConsumptionResult(0L);

        // when
        boolean result =
                emailVerificationStore.consumeSignupToken(
                        SIGNUP_TOKEN_HASH,
                        EMAIL_LOOKUP_HASH
                );

        // then
        assertThat(result).isFalse();
    }

    /**
     * challenge 생성 Lua Script 반환값을 준비합니다.
     */
    private void prepareChallengeCreationResult(
            Long result
    ) {
        when(
                redisTemplate.execute(
                        any(),
                        eq(
                                List.of(
                                        CHALLENGE_KEY,
                                        COOLDOWN_KEY,
                                        REQUEST_COUNT_KEY
                                )
                        ),
                        eq(VERIFICATION_CODE_HASH),
                        eq(
                                Long.toString(
                                        CHALLENGE_TTL.toMillis()
                                )
                        ),
                        eq(
                                Long.toString(
                                        RESEND_COOLDOWN.toMillis()
                                )
                        ),
                        eq(
                                Long.toString(
                                        REQUEST_LIMIT_WINDOW.toMillis()
                                )
                        ),
                        eq(
                                Integer.toString(
                                        MAX_REQUESTS_PER_WINDOW
                                )
                        )
                )
        ).thenReturn(result);
    }

    /**
     * 인증 확인 Lua Script 반환값을 준비합니다.
     */
    private void prepareConfirmationResult(
            Long result
    ) {
        when(
                redisTemplate.execute(
                        any(),
                        eq(
                                List.of(
                                        CHALLENGE_KEY,
                                        SIGNUP_TOKEN_KEY
                                )
                        ),
                        eq(VERIFICATION_CODE_HASH),
                        eq(
                                Long.toString(
                                        SIGNUP_TOKEN_TTL.toMillis()
                                )
                        ),
                        eq(
                                Integer.toString(
                                        MAX_VERIFICATION_ATTEMPTS
                                )
                        ),
                        eq(EMAIL_LOOKUP_HASH)
                )
        ).thenReturn(result);
    }

    /**
     * 회원가입 인증 토큰 소비 Lua Script 반환값을 준비합니다.
     */
    private void prepareTokenConsumptionResult(
            Long result
    ) {
        when(
                redisTemplate.execute(
                        any(),
                        eq(List.of(SIGNUP_TOKEN_KEY)),
                        eq(EMAIL_LOOKUP_HASH)
                )
        ).thenReturn(result);
    }
}
