package com.maesamco.user.infrastructure.security.email;

import com.maesamco.user.application.port.EmailVerificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis를 이용해 이메일 인증 challenge 생성,
 * 인증 코드 확인, signup token 발급 및 일회성 소비를 검증합니다.
 */
@DataRedisTest
@Import(RedisEmailVerificationStore.class)
@Testcontainers
class RedisEmailVerificationStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final String EMAIL_LOOKUP_HASH =
            "email-lookup-hash";

    private static final String OTHER_EMAIL_LOOKUP_HASH =
            "other-email-lookup-hash";

    private static final String VERIFICATION_CODE_HASH =
            "verification-code-hash";

    private static final String WRONG_VERIFICATION_CODE_HASH =
            "wrong-verification-code-hash";

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

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7-alpine")
            ).withExposedPorts(REDIS_PORT);

    @Autowired
    private EmailVerificationStore emailVerificationStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void configureRedis(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.data.redis.host",
                REDIS::getHost
        );

        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(REDIS_PORT)
        );
    }

    @BeforeEach
    void deleteEmailVerificationKeys() {
        Set<String> keys =
                redisTemplate.keys(
                        "email-verification:*"
                );

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName(
            "challenge를 생성하면 인증 코드 hash, cooldown, "
                    + "요청 횟수와 TTL을 Redis에 저장한다"
    )
    void createChallenge_persistsStateWithTtl() {
        // when
        EmailVerificationStore.ChallengeCreationResult result =
                createChallenge(
                        VERIFICATION_CODE_HASH,
                        CHALLENGE_TTL
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ChallengeCreationResult
                                .CREATED
                );

        assertThat(
                redisTemplate.opsForHash().get(
                        CHALLENGE_KEY,
                        "verificationCodeHash"
                )
        ).isEqualTo(VERIFICATION_CODE_HASH);

        assertThat(
                redisTemplate.opsForHash().get(
                        CHALLENGE_KEY,
                        "attempts"
                )
        ).isEqualTo("0");

        assertThat(
                redisTemplate.opsForValue().get(
                        COOLDOWN_KEY
                )
        ).isEqualTo("1");

        assertThat(
                redisTemplate.opsForValue().get(
                        REQUEST_COUNT_KEY
                )
        ).isEqualTo("1");

        assertPositiveTtlWithin(
                CHALLENGE_KEY,
                CHALLENGE_TTL
        );

        assertPositiveTtlWithin(
                COOLDOWN_KEY,
                RESEND_COOLDOWN
        );

        assertPositiveTtlWithin(
                REQUEST_COUNT_KEY,
                REQUEST_LIMIT_WINDOW
        );
    }

    @Test
    @DisplayName(
            "재전송 cooldown 중에는 기존 challenge를 변경하지 않는다"
    )
    void createChallenge_duringCooldownPreservesChallenge() {
        // given
        createChallenge(
                VERIFICATION_CODE_HASH,
                CHALLENGE_TTL
        );

        // when
        EmailVerificationStore.ChallengeCreationResult result =
                createChallenge(
                        "new-verification-code-hash",
                        CHALLENGE_TTL
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ChallengeCreationResult
                                .COOLDOWN_ACTIVE
                );

        assertThat(
                redisTemplate.opsForHash().get(
                        CHALLENGE_KEY,
                        "verificationCodeHash"
                )
        ).isEqualTo(VERIFICATION_CODE_HASH);
    }

    @Test
    @DisplayName(
            "제한 기간 내 이메일 인증 요청 횟수를 초과하면 "
                    + "RATE_LIMIT_EXCEEDED를 반환한다"
    )
    void createChallenge_enforcesRequestRateLimit() {
        // given
        for (int index = 0;
             index < MAX_REQUESTS_PER_WINDOW;
             index++) {

            EmailVerificationStore.ChallengeCreationResult result =
                    createChallenge(
                            VERIFICATION_CODE_HASH,
                            CHALLENGE_TTL
                    );

            assertThat(result)
                    .isEqualTo(
                            EmailVerificationStore
                                    .ChallengeCreationResult
                                    .CREATED
                    );

            redisTemplate.delete(COOLDOWN_KEY);
        }

        // when
        EmailVerificationStore.ChallengeCreationResult result =
                createChallenge(
                        VERIFICATION_CODE_HASH,
                        CHALLENGE_TTL
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ChallengeCreationResult
                                .RATE_LIMIT_EXCEEDED
                );

        assertThat(
                redisTemplate.opsForValue().get(
                        REQUEST_COUNT_KEY
                )
        ).isEqualTo(
                Integer.toString(
                        MAX_REQUESTS_PER_WINDOW
                )
        );
    }

    @Test
    @DisplayName(
            "올바른 인증 코드를 확인하면 challenge를 제거하고 "
                    + "signup token을 발급한다"
    )
    void confirmAndIssueSignupToken_issuesToken() {
        // given
        createChallenge(
                VERIFICATION_CODE_HASH,
                CHALLENGE_TTL
        );

        // when
        EmailVerificationStore.ConfirmationResult result =
                confirm(
                        VERIFICATION_CODE_HASH
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ConfirmationResult
                                .VERIFIED
                );

        assertThat(
                redisTemplate.hasKey(CHALLENGE_KEY)
        ).isFalse();

        assertThat(
                redisTemplate.opsForValue().get(
                        SIGNUP_TOKEN_KEY
                )
        ).isEqualTo(EMAIL_LOOKUP_HASH);

        assertPositiveTtlWithin(
                SIGNUP_TOKEN_KEY,
                SIGNUP_TOKEN_TTL
        );
    }

    @Test
    @DisplayName(
            "잘못된 인증 코드는 실패 횟수를 증가시키고 "
                    + "challenge를 유지한다"
    )
    void confirm_wrongCodeIncrementsAttempts() {
        // given
        createChallenge(
                VERIFICATION_CODE_HASH,
                CHALLENGE_TTL
        );

        Long ttlBefore =
                redisTemplate.getExpire(
                        CHALLENGE_KEY,
                        TimeUnit.MILLISECONDS
                );

        // when
        EmailVerificationStore.ConfirmationResult result =
                confirm(
                        WRONG_VERIFICATION_CODE_HASH
                );

        Long ttlAfter =
                redisTemplate.getExpire(
                        CHALLENGE_KEY,
                        TimeUnit.MILLISECONDS
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ConfirmationResult
                                .INVALID_CODE
                );

        assertThat(
                redisTemplate.opsForHash().get(
                        CHALLENGE_KEY,
                        "attempts"
                )
        ).isEqualTo("1");

        assertThat(ttlBefore)
                .isPositive();

        assertThat(ttlAfter)
                .isPositive()
                .isLessThanOrEqualTo(ttlBefore);
    }

    @Test
    @DisplayName(
            "최대 인증 실패 횟수에 도달하면 challenge를 제거한다"
    )
    void confirm_attemptLimitDeletesChallenge() {
        // given
        createChallenge(
                VERIFICATION_CODE_HASH,
                CHALLENGE_TTL
        );

        for (int attempt = 1;
             attempt < MAX_VERIFICATION_ATTEMPTS;
             attempt++) {

            EmailVerificationStore.ConfirmationResult result =
                    confirm(
                            WRONG_VERIFICATION_CODE_HASH
                    );

            assertThat(result)
                    .isEqualTo(
                            EmailVerificationStore
                                    .ConfirmationResult
                                    .INVALID_CODE
                    );
        }

        // when
        EmailVerificationStore.ConfirmationResult result =
                confirm(
                        WRONG_VERIFICATION_CODE_HASH
                );

        // then
        assertThat(result)
                .isEqualTo(
                        EmailVerificationStore
                                .ConfirmationResult
                                .ATTEMPTS_EXCEEDED
                );

        assertThat(
                redisTemplate.hasKey(CHALLENGE_KEY)
        ).isFalse();
    }

    @Test
    @DisplayName(
            "challenge TTL이 만료되면 EXPIRED를 반환한다"
    )
    void confirm_expiredChallengeReturnsExpired()
            throws InterruptedException {

        // given
        createChallenge(
                VERIFICATION_CODE_HASH,
                Duration.ofMillis(100)
        );

        awaitKeyDeletion(
                CHALLENGE_KEY,
                Duration.ofSeconds(2)
        );

        // when
        EmailVerificationStore.ConfirmationResult result =
                confirm(
                        VERIFICATION_CODE_HASH
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
    @DisplayName(
            "signup token은 발급된 이메일에만 사용할 수 있다"
    )
    void consumeSignupToken_rejectsDifferentEmail() {
        // given
        issueSignupToken();

        // when
        boolean wrongEmailResult =
                emailVerificationStore.consumeSignupToken(
                        SIGNUP_TOKEN_HASH,
                        OTHER_EMAIL_LOOKUP_HASH
                );

        boolean correctEmailResult =
                emailVerificationStore.consumeSignupToken(
                        SIGNUP_TOKEN_HASH,
                        EMAIL_LOOKUP_HASH
                );

        // then
        assertThat(wrongEmailResult).isFalse();
        assertThat(correctEmailResult).isTrue();
    }

    @Test
    @DisplayName(
            "signup token은 한 번만 소비할 수 있다"
    )
    void consumeSignupToken_canBeConsumedOnlyOnce() {
        // given
        issueSignupToken();

        // when
        boolean firstResult =
                emailVerificationStore.consumeSignupToken(
                        SIGNUP_TOKEN_HASH,
                        EMAIL_LOOKUP_HASH
                );

        boolean secondResult =
                emailVerificationStore.consumeSignupToken(
                        SIGNUP_TOKEN_HASH,
                        EMAIL_LOOKUP_HASH
                );

        // then
        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();

        assertThat(
                redisTemplate.hasKey(SIGNUP_TOKEN_KEY)
        ).isFalse();
    }

    @Test
    @DisplayName(
            "같은 signup token을 동시에 소비하면 하나의 요청만 성공한다"
    )
    void consumeSignupToken_concurrentRequestsAreAtomic()
            throws Exception {

        // given
        issueSignupToken();

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> firstFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return emailVerificationStore
                                .consumeSignupToken(
                                        SIGNUP_TOKEN_HASH,
                                        EMAIL_LOOKUP_HASH
                                );
                    });

            Future<Boolean> secondFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return emailVerificationStore
                                .consumeSignupToken(
                                        SIGNUP_TOKEN_HASH,
                                        EMAIL_LOOKUP_HASH
                                );
                    });

            assertThat(
                    readyLatch.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // when
            startLatch.countDown();

            boolean firstResult =
                    firstFuture.get(
                            5,
                            TimeUnit.SECONDS
                    );

            boolean secondResult =
                    secondFuture.get(
                            5,
                            TimeUnit.SECONDS
                    );

            // then
            assertThat(
                    List.of(
                            firstResult,
                            secondResult
                    )
            ).containsExactlyInAnyOrder(
                    true,
                    false
            );

            assertThat(
                    redisTemplate.hasKey(
                            SIGNUP_TOKEN_KEY
                    )
            ).isFalse();
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 테스트용 이메일 인증 challenge를 생성합니다.
     */
    private EmailVerificationStore.ChallengeCreationResult
    createChallenge(
            String verificationCodeHash,
            Duration challengeTtl
    ) {
        return emailVerificationStore.createChallenge(
                EMAIL_LOOKUP_HASH,
                verificationCodeHash,
                challengeTtl,
                RESEND_COOLDOWN,
                REQUEST_LIMIT_WINDOW,
                MAX_REQUESTS_PER_WINDOW
        );
    }

    /**
     * 테스트용 인증 코드 확인을 수행합니다.
     */
    private EmailVerificationStore.ConfirmationResult confirm(
            String verificationCodeHash
    ) {
        return emailVerificationStore
                .confirmAndIssueSignupToken(
                        EMAIL_LOOKUP_HASH,
                        verificationCodeHash,
                        SIGNUP_TOKEN_HASH,
                        SIGNUP_TOKEN_TTL,
                        MAX_VERIFICATION_ATTEMPTS
                );
    }

    /**
     * 정상적인 challenge 생성과 확인을 통해
     * signup token을 발급합니다.
     */
    private void issueSignupToken() {
        EmailVerificationStore.ChallengeCreationResult
                creationResult =
                createChallenge(
                        VERIFICATION_CODE_HASH,
                        CHALLENGE_TTL
                );

        assertThat(creationResult)
                .isEqualTo(
                        EmailVerificationStore
                                .ChallengeCreationResult
                                .CREATED
                );

        EmailVerificationStore.ConfirmationResult
                confirmationResult =
                confirm(
                        VERIFICATION_CODE_HASH
                );

        assertThat(confirmationResult)
                .isEqualTo(
                        EmailVerificationStore
                                .ConfirmationResult
                                .VERIFIED
                );
    }

    /**
     * Redis Key의 TTL이 양수이고 예상 TTL 이하인지 확인합니다.
     */
    private void assertPositiveTtlWithin(
            String key,
            Duration expectedTtl
    ) {
        Long ttl =
                redisTemplate.getExpire(
                        key,
                        TimeUnit.MILLISECONDS
                );

        assertThat(ttl)
                .isPositive()
                .isLessThanOrEqualTo(
                        expectedTtl.toMillis()
                );
    }

    /**
     * Redis TTL에 의해 Key가 실제로 제거될 때까지 기다립니다.
     */
    private void awaitKeyDeletion(
            String key,
            Duration timeout
    ) throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (Boolean.FALSE.equals(
                    redisTemplate.hasKey(key)
            )) {
                return;
            }

            Thread.sleep(10);
        }

        assertThat(
                redisTemplate.hasKey(key)
        ).isFalse();
    }
}
