package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.*;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 Redis를 이용해 인증 세션 저장, TTL,
 * Refresh Token Rotation 및 Reuse Detection을 검증합니다.
 */
@DataRedisTest
@Import({
        RedisAuthSessionStore.class,
        RedisAuthSessionLogoutStore.class,
        RedisAuthSessionLogoutAllStore.class,
        RedisAuthSessionStoreIntegrationTest.TestConfig.class
})
@Testcontainers
class RedisAuthSessionStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final Instant NOW =
            Instant.parse("2026-09-02T00:00:00Z");

    private static final Duration SESSION_TTL =
            Duration.ofDays(14);

    private static final Duration ROTATION_GRACE_PERIOD =
            Duration.ofSeconds(5);

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID FAMILY_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID USER_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final UUID OTHER_USER_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID SECOND_SESSION_ID =
            UUID.fromString(
                    "55555555-5555-5555-5555-555555555555"
            );

    private static final UUID SECOND_FAMILY_ID =
            UUID.fromString(
                    "66666666-6666-6666-6666-666666666666"
            );

    private static final String SESSION_KEY =
            "session:" + SESSION_ID;

    private static final String USER_SESSION_INDEX_KEY =
            "user:" + USER_ID + ":sessions";

    private static final String SESSION_BLACKLIST_KEY =
            SESSION_KEY + ":blacklisted";

    private static final String SECOND_SESSION_KEY =
            "session:" + SECOND_SESSION_ID;

    private static final String SECOND_SESSION_BLACKLIST_KEY =
            SECOND_SESSION_KEY + ":blacklisted";

    private static final Instant ACCESS_TOKEN_EXPIRES_AT =
            NOW.plusSeconds(900);

    private static final String ORIGINAL_REFRESH_TOKEN_HASH =
            "refresh-token-hash-a";

    private static final String ROTATED_REFRESH_TOKEN_HASH =
            "refresh-token-hash-b";

    private static final String SECOND_ROTATED_REFRESH_TOKEN_HASH =
            "refresh-token-hash-c";

    private static final String SECOND_SESSION_REFRESH_TOKEN_HASH =
            "refresh-token-hash-second-session";

    private static final Duration ACCESS_TOKEN_TTL =
            Duration.ofMinutes(15);

    private static final UUID OTHER_SESSION_ID =
            UUID.fromString(
                    "77777777-7777-7777-7777-777777777777"
            );

    private static final UUID OTHER_FAMILY_ID =
            UUID.fromString(
                    "88888888-8888-8888-8888-888888888888"
            );

    private static final String USER_INVALIDATED_AT_KEY =
            "user:" + USER_ID + ":invalidatedAt";

    private static final String OTHER_SESSION_KEY =
            "session:" + OTHER_SESSION_ID;

    private static final String OTHER_USER_SESSION_INDEX_KEY =
            "user:" + OTHER_USER_ID + ":sessions";

    private static final String OTHER_USER_INVALIDATED_AT_KEY =
            "user:" + OTHER_USER_ID + ":invalidatedAt";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7-alpine")
            ).withExposedPorts(REDIS_PORT);

    @Autowired
    private AuthSessionStore authSessionStore;

    @Autowired
    private AuthSessionLogoutStore authSessionLogoutStore;

    @Autowired
    private AuthSessionLogoutAllStore authSessionLogoutAllStore;

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
    void deleteTestSession() {
        redisTemplate.delete(
                List.of(
                        SESSION_KEY,
                        SESSION_BLACKLIST_KEY,
                        SECOND_SESSION_KEY,
                        SECOND_SESSION_BLACKLIST_KEY,
                        USER_SESSION_INDEX_KEY,
                        USER_INVALIDATED_AT_KEY,
                        OTHER_SESSION_KEY,
                        OTHER_USER_SESSION_INDEX_KEY,
                        OTHER_USER_INVALIDATED_AT_KEY
                )
        );
    }

    @Test
    @DisplayName("인증 세션을 실제 Redis에 저장하고 조회한다")
    void saveAndFind_persistsSessionInRedis() {
        // given
        AuthSession session = createSession();

        // when
        authSessionStore.save(session);

        // then
        assertThat(
                authSessionStore.findBySessionId(SESSION_ID)
        ).contains(session);

        assertThat(
                redisTemplate.opsForValue().get(SESSION_KEY)
        )
                .contains("\"refreshTokenHash\"")
                .contains(ORIGINAL_REFRESH_TOKEN_HASH);
    }

    @Test
    @DisplayName(
            "인증 세션 저장 시 사용자별 세션 인덱스와 TTL도 함께 저장한다"
    )
    void save_registersSessionInUserIndexWithTtl() {
        // given
        AuthSession session =
                createSession();

        // when
        authSessionStore.save(
                session
        );

        // then
        assertThat(
                redisTemplate
                        .opsForSet()
                        .members(
                                USER_SESSION_INDEX_KEY
                        )
        )
                .containsExactly(
                        SESSION_ID.toString()
                );

        Long remainingIndexTtl =
                redisTemplate.getExpire(
                        USER_SESSION_INDEX_KEY,
                        TimeUnit.SECONDS
                );

        assertThat(remainingIndexTtl)
                .isBetween(
                        SESSION_TTL
                                .minusSeconds(5)
                                .getSeconds(),
                        SESSION_TTL.getSeconds()
                );
    }

    @Test
    @DisplayName(
            "짧은 세션을 추가해도 사용자 세션 인덱스 TTL을 줄이지 않는다"
    )
    void save_shorterSessionDoesNotShortenUserIndexTtl() {
        // given
        AuthSession longLivedSession =
                createSession();

        authSessionStore.save(
                longLivedSession
        );

        Long ttlBefore =
                redisTemplate.getExpire(
                        USER_SESSION_INDEX_KEY,
                        TimeUnit.MILLISECONDS
                );

        AuthSession shortLivedSession =
                new AuthSession(
                        SECOND_SESSION_ID,
                        SECOND_FAMILY_ID,
                        USER_ID,
                        SECOND_SESSION_REFRESH_TOKEN_HASH,
                        NOW,
                        NOW.plus(Duration.ofDays(1))
                );

        // when
        authSessionStore.save(
                shortLivedSession
        );

        Long ttlAfter =
                redisTemplate.getExpire(
                        USER_SESSION_INDEX_KEY,
                        TimeUnit.MILLISECONDS
                );

        // then
        assertThat(
                redisTemplate
                        .opsForSet()
                        .members(
                                USER_SESSION_INDEX_KEY
                        )
        )
                .containsExactlyInAnyOrder(
                        SESSION_ID.toString(),
                        SECOND_SESSION_ID.toString()
                );

        assertThat(ttlBefore)
                .isPositive();

        assertThat(ttlAfter)
                .isPositive();

        assertThat(ttlAfter)
                .isGreaterThan(
                        Duration.ofDays(13)
                                .toMillis()
                );

        assertThat(ttlAfter)
                .isLessThanOrEqualTo(
                        ttlBefore
                );
    }

    @Test
    @DisplayName("인증 세션 만료 시각에 맞춰 Redis TTL을 설정한다")
    void save_setsRedisTtl() {
        // given
        AuthSession session = createSession();

        // when
        authSessionStore.save(session);

        // then
        Long remainingTtl = redisTemplate.getExpire(
                SESSION_KEY,
                TimeUnit.SECONDS
        );

        assertThat(remainingTtl)
                .isBetween(
                        SESSION_TTL.minusSeconds(5).getSeconds(),
                        SESSION_TTL.getSeconds()
                );
    }

    @Test
    @DisplayName("현재 Refresh Token hash가 일치하면 새로운 hash로 Rotation한다")
    void rotateRefreshToken_replacesRefreshTokenHash() {
        // given
        AuthSession originalSession = createSession();
        authSessionStore.save(originalSession);

        // when
        AuthSessionRotationResult result =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        USER_ID,
                        NOW,
                        ORIGINAL_REFRESH_TOKEN_HASH,
                        ROTATED_REFRESH_TOKEN_HASH
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionRotationResult.ROTATED
                );

        AuthSession rotatedSession =
                authSessionStore.findBySessionId(SESSION_ID)
                        .orElseThrow();

        assertThat(rotatedSession.refreshTokenHash())
                .isEqualTo(ROTATED_REFRESH_TOKEN_HASH);

        assertThat(rotatedSession.sessionId())
                .isEqualTo(originalSession.sessionId());

        assertThat(rotatedSession.familyId())
                .isEqualTo(originalSession.familyId());

        assertThat(rotatedSession.userId())
                .isEqualTo(originalSession.userId());

        assertThat(rotatedSession.createdAt())
                .isEqualTo(originalSession.createdAt());

        assertThat(rotatedSession.expiresAt())
                .isEqualTo(originalSession.expiresAt());
    }

    @Test
    @DisplayName("Refresh Token Rotation 후에도 기존 Redis TTL을 연장하지 않는다")
    void rotateRefreshToken_preservesRemainingTtl() {
        // given
        authSessionStore.save(createSession());

        Long ttlBeforeRotation =
                redisTemplate.getExpire(
                        SESSION_KEY,
                        TimeUnit.MILLISECONDS
                );

        // when
        AuthSessionRotationResult result =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        USER_ID,
                        NOW,
                        ORIGINAL_REFRESH_TOKEN_HASH,
                        ROTATED_REFRESH_TOKEN_HASH
                );

        Long ttlAfterRotation =
                redisTemplate.getExpire(
                        SESSION_KEY,
                        TimeUnit.MILLISECONDS
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionRotationResult.ROTATED
                );

        assertThat(ttlBeforeRotation)
                .isPositive();

        assertThat(ttlAfterRotation)
                .isPositive();

        assertThat(ttlAfterRotation)
                .isLessThanOrEqualTo(ttlBeforeRotation);

        assertThat(ttlAfterRotation)
                .isGreaterThan(
                        ttlBeforeRotation - 5_000L
                );
    }

    @Test
    @DisplayName(
            "grace window 안에서 직전 Refresh Token이 다시 요청되면 "
                    + "세션과 현재 hash를 유지한다"
    )
    void rotateRefreshToken_previousTokenWithinGracePreservesSession() {
        // given
        AuthSession rotatedSession = new AuthSession(
                SESSION_ID,
                FAMILY_ID,
                USER_ID,
                ROTATED_REFRESH_TOKEN_HASH,
                NOW,
                NOW.plus(SESSION_TTL),
                ORIGINAL_REFRESH_TOKEN_HASH,
                NOW.minus(ROTATION_GRACE_PERIOD)
                        .plusMillis(1)
                        .toEpochMilli()
        );

        authSessionStore.save(rotatedSession);

        // when
        AuthSessionRotationResult previousTokenResult =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        USER_ID,
                        NOW,
                        ORIGINAL_REFRESH_TOKEN_HASH,
                        SECOND_ROTATED_REFRESH_TOKEN_HASH
                );

        AuthSession preservedSession =
                authSessionStore
                        .findBySessionId(SESSION_ID)
                        .orElseThrow();

        // then
        assertThat(previousTokenResult)
                .isEqualTo(
                        AuthSessionRotationResult
                                .PREVIOUS_TOKEN_WITHIN_GRACE
                );

        assertThat(preservedSession.refreshTokenHash())
                .isEqualTo(
                        ROTATED_REFRESH_TOKEN_HASH
                );

        assertThat(
                preservedSession.previousRefreshTokenHash()
        ).isEqualTo(
                ORIGINAL_REFRESH_TOKEN_HASH
        );

        assertThat(
                redisTemplate.hasKey(SESSION_KEY)
        ).isTrue();

        // when
        AuthSessionRotationResult latestTokenResult =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        USER_ID,
                        NOW,
                        ROTATED_REFRESH_TOKEN_HASH,
                        SECOND_ROTATED_REFRESH_TOKEN_HASH
                );

        AuthSession subsequentlyRotatedSession =
                authSessionStore
                        .findBySessionId(SESSION_ID)
                        .orElseThrow();

        // then
        assertThat(latestTokenResult)
                .isEqualTo(
                        AuthSessionRotationResult.ROTATED
                );

        assertThat(
                subsequentlyRotatedSession.refreshTokenHash()
        ).isEqualTo(
                SECOND_ROTATED_REFRESH_TOKEN_HASH
        );

        assertThat(
                subsequentlyRotatedSession
                        .previousRefreshTokenHash()
        ).isEqualTo(
                ROTATED_REFRESH_TOKEN_HASH
        );
    }

    @Test
    @DisplayName("grace window가 지난 이전 Refresh Token을 재사용하면 세션을 폐기한다")
    void rotateRefreshToken_previousTokenOutsideGraceDeletesSession() {
        // given
        AuthSession rotatedSession = new AuthSession(
                SESSION_ID,
                FAMILY_ID,
                USER_ID,
                ROTATED_REFRESH_TOKEN_HASH,
                NOW,
                NOW.plus(SESSION_TTL),
                ORIGINAL_REFRESH_TOKEN_HASH,
                NOW.minus(ROTATION_GRACE_PERIOD)
                        .minusMillis(1)
                        .toEpochMilli()
        );

        authSessionStore.save(rotatedSession);

        // when
        AuthSessionRotationResult reuseResult =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        USER_ID,
                        NOW,
                        ORIGINAL_REFRESH_TOKEN_HASH,
                        SECOND_ROTATED_REFRESH_TOKEN_HASH
                );

        // then
        assertThat(reuseResult)
                .isEqualTo(
                        AuthSessionRotationResult.TOKEN_REUSED
                );

        assertThat(
                authSessionStore.findBySessionId(SESSION_ID)
        ).isEmpty();

        assertThat(
                redisTemplate.hasKey(SESSION_KEY)
        ).isFalse();
    }

    @Test
    @DisplayName("grace window 안에서도 직전 토큰이 아닌 Refresh Token이면 세션을 폐기한다")
    void rotateRefreshToken_unrelatedTokenWithinGraceDeletesSession() {
        // given
        AuthSession rotatedSession = new AuthSession(
                SESSION_ID,
                FAMILY_ID,
                USER_ID,
                ROTATED_REFRESH_TOKEN_HASH,
                NOW,
                NOW.plus(SESSION_TTL),
                ORIGINAL_REFRESH_TOKEN_HASH,
                NOW.toEpochMilli()
        );

        authSessionStore.save(rotatedSession);

        // when
        AuthSessionRotationResult reuseResult =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        USER_ID,
                        NOW,
                        "unrelated-refresh-token-hash",
                        SECOND_ROTATED_REFRESH_TOKEN_HASH
                );

        // then
        assertThat(reuseResult)
                .isEqualTo(
                        AuthSessionRotationResult.TOKEN_REUSED
                );

        assertThat(
                authSessionStore.findBySessionId(SESSION_ID)
        ).isEmpty();

        assertThat(
                redisTemplate.hasKey(SESSION_KEY)
        ).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 인증 세션을 Rotation하면 SESSION_NOT_FOUND를 반환한다")
    void rotateRefreshToken_missingSessionReturnsSessionNotFound() {
        // when
        AuthSessionRotationResult result =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        USER_ID,
                        NOW,
                        ORIGINAL_REFRESH_TOKEN_HASH,
                        ROTATED_REFRESH_TOKEN_HASH
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionRotationResult.SESSION_NOT_FOUND
                );
    }

    @Test
    @DisplayName("같은 Refresh Token으로 동시에 Rotation하면 하나만 성공하고 정상 세션은 유지한다")
    void rotateRefreshToken_concurrentRequestsAreAtomic()
            throws Exception {
        // given
        authSessionStore.save(createSession());

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            Future<AuthSessionRotationResult> firstFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return authSessionStore.rotateRefreshToken(
                                SESSION_ID,
                                USER_ID,
                                NOW,
                                ORIGINAL_REFRESH_TOKEN_HASH,
                                ROTATED_REFRESH_TOKEN_HASH
                        );
                    });

            Future<AuthSessionRotationResult> secondFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return authSessionStore.rotateRefreshToken(
                                SESSION_ID,
                                USER_ID,
                                NOW,
                                ORIGINAL_REFRESH_TOKEN_HASH,
                                SECOND_ROTATED_REFRESH_TOKEN_HASH
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

            AuthSessionRotationResult firstResult =
                    firstFuture.get(
                            5,
                            TimeUnit.SECONDS
                    );

            AuthSessionRotationResult secondResult =
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
            )
                    .containsExactlyInAnyOrder(
                            AuthSessionRotationResult.ROTATED,
                            AuthSessionRotationResult
                                    .PREVIOUS_TOKEN_WITHIN_GRACE
                    );

            assertThat(
                    authSessionStore.findBySessionId(SESSION_ID)
            ).isPresent();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("인증 세션을 실제 Redis에서 삭제한다")
    void delete_removesSessionFromRedis() {
        // given
        authSessionStore.save(createSession());

        // when
        authSessionStore.deleteBySessionId(SESSION_ID);

        // then
        assertThat(
                authSessionStore.findBySessionId(SESSION_ID)
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "로그아웃하면 실제 Redis 인증 세션을 삭제하고 "
                    + "세션 만료 시각까지 블랙리스트를 유지한다"
    )
    void logout_deletesSessionAndCreatesBlacklist() {
        // given
        authSessionStore.save(
                createSession()
        );

        // when
        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionLogoutResult.LOGGED_OUT
                );

        assertThat(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).isEmpty();

        assertThat(
                redisTemplate.hasKey(
                        SESSION_BLACKLIST_KEY
                )
        ).isTrue();

        assertThat(
                redisTemplate.opsForValue()
                        .get(SESSION_BLACKLIST_KEY)
        ).isEqualTo("1");

        Long blacklistTtl =
                redisTemplate.getExpire(
                        SESSION_BLACKLIST_KEY,
                        TimeUnit.MILLISECONDS
                );

        long expectedSessionTtlMillis =
                SESSION_TTL.toMillis();

        assertThat(blacklistTtl)
                .isBetween(
                        expectedSessionTtlMillis - 5_000L,
                        expectedSessionTtlMillis
                );
    }

    @Test
    @DisplayName(
            "현재 세션을 로그아웃해도 "
                    + "동일 사용자의 다른 인증 세션은 유지한다"
    )
    void logout_keepsOtherSessionOfSameUser() {
        // given
        AuthSession currentSession =
                createSession();

        AuthSession otherSession =
                createSecondSession();

        authSessionStore.save(currentSession);
        authSessionStore.save(otherSession);

        // when
        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionLogoutResult.LOGGED_OUT
                );

        assertThat(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).isEmpty();

        assertThat(
                redisTemplate.hasKey(
                        SESSION_BLACKLIST_KEY
                )
        ).isTrue();

        assertThat(
                authSessionStore.findBySessionId(
                        SECOND_SESSION_ID
                )
        ).contains(otherSession);

        assertThat(
                redisTemplate.hasKey(
                        SECOND_SESSION_KEY
                )
        ).isTrue();

        assertThat(
                redisTemplate.hasKey(
                        SECOND_SESSION_BLACKLIST_KEY
                )
        ).isFalse();
    }

    @Test
    @DisplayName(
            "인증 세션이 이미 없어도 실제 Redis에 "
                    + "세션 블랙리스트를 등록한다"
    )
    void logout_missingSessionCreatesBlacklist() {
        // when
        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionLogoutResult
                                .SESSION_NOT_FOUND
                );

        assertThat(
                redisTemplate.hasKey(
                        SESSION_BLACKLIST_KEY
                )
        ).isTrue();

        assertThat(
                redisTemplate.opsForValue()
                        .get(SESSION_BLACKLIST_KEY)
        ).isEqualTo("1");
    }

    @Test
    @DisplayName(
            "인증된 사용자와 실제 Redis 세션 소유자가 다르면 "
                    + "세션과 블랙리스트를 변경하지 않는다"
    )
    void logout_ownerMismatchDoesNotChangeRedis() {
        // given
        AuthSession session =
                createSession();

        authSessionStore.save(session);

        // when
        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        OTHER_USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionLogoutResult
                                .SESSION_OWNER_MISMATCH
                );

        assertThat(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).contains(session);

        assertThat(
                redisTemplate.hasKey(
                        SESSION_BLACKLIST_KEY
                )
        ).isFalse();
    }

    private AuthSession createSession() {
        return new AuthSession(
                SESSION_ID,
                FAMILY_ID,
                USER_ID,
                ORIGINAL_REFRESH_TOKEN_HASH,
                NOW,
                NOW.plus(SESSION_TTL)
        );
    }

    private AuthSession createSecondSession() {
        return new AuthSession(
                SECOND_SESSION_ID,
                SECOND_FAMILY_ID,
                USER_ID,
                SECOND_SESSION_REFRESH_TOKEN_HASH,
                NOW,
                NOW.plus(SESSION_TTL)
        );
    }

    private AuthSession createOtherUserSession() {
        return new AuthSession(
                OTHER_SESSION_ID,
                OTHER_FAMILY_ID,
                OTHER_USER_ID,
                "refresh-token-hash-other-user",
                NOW,
                NOW.plus(SESSION_TTL)
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        Clock testClock() {
            return Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            );
        }

        @Bean
        JwtProperties testJwtProperties() {
            return new JwtProperties(
                    null,
                    null,
                    null,
                    null,
                    ACCESS_TOKEN_TTL,
                    SESSION_TTL
            );
        }

        @Bean
        AuthSessionProperties testAuthSessionProperties() {
            return new AuthSessionProperties(
                    Duration.ofSeconds(5)
            );
        }

        @Bean
        JsonMapper testJsonMapper() {
            return JsonMapper.builder()
                    .findAndAddModules()
                    .build();
        }
    }

    @Test
    @DisplayName(
            "기존 세션 블랙리스트 TTL이 더 길면 "
                    + "로그아웃 시 TTL을 단축하지 않는다"
    )
    void logout_doesNotShortenExistingBlacklistTtl() {
        // given
        authSessionStore.save(
                createSession()
        );

        Duration existingBlacklistTtl =
                Duration.ofDays(30);

        redisTemplate.opsForValue().set(
                SESSION_BLACKLIST_KEY,
                "1",
                existingBlacklistTtl
        );

        Long ttlBeforeLogout =
                redisTemplate.getExpire(
                        SESSION_BLACKLIST_KEY,
                        TimeUnit.MILLISECONDS
                );

        // when
        AuthSessionLogoutResult result =
                authSessionLogoutStore.logout(
                        USER_ID,
                        SESSION_ID,
                        ACCESS_TOKEN_EXPIRES_AT
                );

        Long ttlAfterLogout =
                redisTemplate.getExpire(
                        SESSION_BLACKLIST_KEY,
                        TimeUnit.MILLISECONDS
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionLogoutResult.LOGGED_OUT
                );

        assertThat(ttlBeforeLogout)
                .isBetween(
                        existingBlacklistTtl.toMillis() - 5_000L,
                        existingBlacklistTtl.toMillis()
                );

        assertThat(ttlAfterLogout)
                .isPositive();

        assertThat(ttlAfterLogout)
                .isLessThanOrEqualTo(ttlBeforeLogout);

        assertThat(ttlAfterLogout)
                .isGreaterThan(
                        ttlBeforeLogout - 5_000L
                );

        assertThat(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).isEmpty();
    }

    @Test
    @DisplayName(
            "전체 로그아웃하면 같은 사용자의 모든 세션과 인덱스를 삭제하고 "
                    + "다른 사용자의 세션은 유지한다"
    )
    void logoutAll_deletesOnlyTargetUsersSessions() {
        // given
        AuthSession firstSession =
                createSession();

        AuthSession secondSession =
                createSecondSession();

        AuthSession otherUserSession =
                createOtherUserSession();

        authSessionStore.save(firstSession);
        authSessionStore.save(secondSession);
        authSessionStore.save(otherUserSession);

        // when
        authSessionLogoutAllStore.logoutAll(
                USER_ID,
                NOW
        );

        // then
        assertThat(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).isEmpty();

        assertThat(
                authSessionStore.findBySessionId(
                        SECOND_SESSION_ID
                )
        ).isEmpty();

        assertThat(
                redisTemplate.hasKey(
                        USER_SESSION_INDEX_KEY
                )
        ).isFalse();

        assertThat(
                authSessionStore.findBySessionId(
                        OTHER_SESSION_ID
                )
        ).contains(otherUserSession);

        assertThat(
                redisTemplate.hasKey(
                        OTHER_SESSION_KEY
                )
        ).isTrue();

        assertThat(
                redisTemplate
                        .opsForSet()
                        .members(
                                OTHER_USER_SESSION_INDEX_KEY
                        )
        ).containsExactly(
                OTHER_SESSION_ID.toString()
        );
    }

    @Test
    @DisplayName(
            "전체 로그아웃 시 사용자 무효화 시각을 Access Token TTL 동안 저장한다"
    )
    void logoutAll_storesUserInvalidatedAtWithAccessTokenTtl() {
        // given
        authSessionStore.save(
                createSession()
        );

        // when
        authSessionLogoutAllStore.logoutAll(
                USER_ID,
                NOW
        );

        // then
        assertThat(
                redisTemplate
                        .opsForValue()
                        .get(
                                USER_INVALIDATED_AT_KEY
                        )
        ).isEqualTo(
                Long.toString(
                        NOW.toEpochMilli()
                )
        );

        Long remainingTtl =
                redisTemplate.getExpire(
                        USER_INVALIDATED_AT_KEY,
                        TimeUnit.MILLISECONDS
                );

        assertThat(remainingTtl)
                .isBetween(
                        ACCESS_TOKEN_TTL.toMillis() - 5_000L,
                        ACCESS_TOKEN_TTL.toMillis()
                );
    }

    @Test
    @DisplayName(
            "전체 로그아웃을 반복 호출해도 안전하게 성공하고 무효화 정보를 유지한다"
    )
    void logoutAll_repeatedRequestIsIdempotent() {
        // given
        authSessionStore.save(
                createSession()
        );

        authSessionLogoutAllStore.logoutAll(
                USER_ID,
                NOW
        );

        // when
        authSessionLogoutAllStore.logoutAll(
                USER_ID,
                NOW
        );

        // then
        assertThat(
                authSessionStore.findBySessionId(
                        SESSION_ID
                )
        ).isEmpty();

        assertThat(
                redisTemplate.hasKey(
                        USER_SESSION_INDEX_KEY
                )
        ).isFalse();

        assertThat(
                redisTemplate
                        .opsForValue()
                        .get(
                                USER_INVALIDATED_AT_KEY
                        )
        ).isEqualTo(
                Long.toString(
                        NOW.toEpochMilli()
                )
        );

        assertThat(
                redisTemplate.getExpire(
                        USER_INVALIDATED_AT_KEY,
                        TimeUnit.MILLISECONDS
                )
        ).isPositive();
    }

    @Test
    @DisplayName(
            "사용자 세션 인덱스에 없는 기존 세션도 "
                    + "전체 로그아웃 이후 Refresh Token Rotation을 차단한다"
    )
    void rotateRefreshToken_rejectsLegacySessionInvalidatedByLogoutAll() {
        // given
        AuthSession legacySession =
                createSession();

        authSessionStore.save(
                legacySession
        );

        /*
         * 배포 이전에 생성되어
         * user:{userId}:sessions 인덱스에 등록되지 않았던
         * 기존 인증 세션을 재현합니다.
         */
        redisTemplate
                .opsForSet()
                .remove(
                        USER_SESSION_INDEX_KEY,
                        SESSION_ID.toString()
                );

        assertThat(
                redisTemplate.hasKey(
                        SESSION_KEY
                )
        ).isTrue();

        assertThat(
                redisTemplate.hasKey(
                        USER_SESSION_INDEX_KEY
                )
        ).isFalse();

        Instant invalidatedAt =
                NOW.plusMillis(1);

        authSessionLogoutAllStore.logoutAll(
                USER_ID,
                invalidatedAt
        );

        /*
         * 인덱스에 없었기 때문에 logoutAll의 세션 순회에서는
         * 이 legacy session을 직접 삭제하지 못합니다.
         */
        assertThat(
                redisTemplate.hasKey(
                        SESSION_KEY
                )
        ).isTrue();

        // when
        AuthSessionRotationResult result =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        USER_ID,
                        legacySession.createdAt(),
                        ORIGINAL_REFRESH_TOKEN_HASH,
                        ROTATED_REFRESH_TOKEN_HASH
                );

        // then
        assertThat(result)
                .isEqualTo(
                        AuthSessionRotationResult
                                .SESSION_NOT_FOUND
                );

        assertThat(
                redisTemplate.hasKey(
                        SESSION_KEY
                )
        ).isFalse();
    }

    @Test
    @DisplayName(
            "전체 로그아웃 이전에 시작된 인증 세션이 "
                    + "뒤늦게 저장되면 거부한다"
    )
    void save_afterLogoutAllRejectsSessionStartedBeforeInvalidation() {
        // given
        authSessionLogoutAllStore.logoutAll(
                USER_ID,
                NOW
        );

        AuthSession staleSession =
                new AuthSession(
                        SECOND_SESSION_ID,
                        SECOND_FAMILY_ID,
                        USER_ID,
                        SECOND_SESSION_REFRESH_TOKEN_HASH,
                        NOW.minusMillis(1),
                        NOW.plus(SESSION_TTL)
                );

        // when & then
        assertThatThrownBy(
                () -> authSessionStore.save(
                        staleSession
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                ErrorCode.AUTH_TOKEN_REVOKED
                        )
                );

        assertThat(
                redisTemplate.hasKey(
                        SECOND_SESSION_KEY
                )
        ).isFalse();

        assertThat(
                redisTemplate.hasKey(
                        USER_SESSION_INDEX_KEY
                )
        ).isFalse();
    }

    @Test
    @DisplayName(
            "동일 사용자의 전체 로그아웃이 동시에 실행되어도 "
                    + "가장 최신 무효화 시각을 유지한다"
    )
    void logoutAll_concurrentRequestsKeepLatestInvalidatedAt()
            throws Exception {
        // given
        authSessionStore.save(
                createSession()
        );

        Instant firstInvalidatedAt =
                NOW.plusMillis(1);

        Instant secondInvalidatedAt =
                NOW.plusMillis(2);

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> firstFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        authSessionLogoutAllStore.logoutAll(
                                USER_ID,
                                firstInvalidatedAt
                        );

                        return null;
                    });

            Future<?> secondFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        authSessionLogoutAllStore.logoutAll(
                                USER_ID,
                                secondInvalidatedAt
                        );

                        return null;
                    });

            assertThat(
                    readyLatch.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // when
            startLatch.countDown();

            firstFuture.get(
                    5,
                    TimeUnit.SECONDS
            );

            secondFuture.get(
                    5,
                    TimeUnit.SECONDS
            );

            // then
            assertThat(
                    authSessionStore.findBySessionId(
                            SESSION_ID
                    )
            ).isEmpty();

            assertThat(
                    redisTemplate.hasKey(
                            USER_SESSION_INDEX_KEY
                    )
            ).isFalse();

            assertThat(
                    redisTemplate
                            .opsForValue()
                            .get(
                                    USER_INVALIDATED_AT_KEY
                            )
            ).isEqualTo(
                    Long.toString(
                            secondInvalidatedAt.toEpochMilli()
                    )
            );

            assertThat(
                    redisTemplate.getExpire(
                            USER_INVALIDATED_AT_KEY,
                            TimeUnit.MILLISECONDS
                    )
            ).isPositive();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName(
            "전체 로그아웃과 이전에 시작된 인증 세션 저장이 동시에 실행되어도 "
                    + "무효화된 세션은 남지 않는다"
    )
    void logoutAll_concurrentSaveDoesNotLeaveStaleSession()
            throws Exception {
        // given
        Instant invalidatedAt =
                NOW.plusMillis(1);

        AuthSession staleSession =
                new AuthSession(
                        SESSION_ID,
                        FAMILY_ID,
                        USER_ID,
                        ORIGINAL_REFRESH_TOKEN_HASH,
                        NOW,
                        NOW.plus(SESSION_TTL)
                );

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> logoutAllFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        authSessionLogoutAllStore.logoutAll(
                                USER_ID,
                                invalidatedAt
                        );

                        return null;
                    });

            Future<Throwable> saveFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        try {
                            authSessionStore.save(
                                    staleSession
                            );

                            return null;
                        } catch (Throwable throwable) {
                            return throwable;
                        }
                    });

            assertThat(
                    readyLatch.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            // when
            startLatch.countDown();

            logoutAllFuture.get(
                    5,
                    TimeUnit.SECONDS
            );

            Throwable saveFailure =
                    saveFuture.get(
                            5,
                            TimeUnit.SECONDS
                    );

            // then
            /*
             * save가 먼저 끝난 경우:
             * logoutAll이 인덱스를 통해 세션을 삭제합니다.
             *
             * logoutAll이 먼저 끝난 경우:
             * save가 invalidatedAt을 보고 AUTH_TOKEN_REVOKED로 거부됩니다.
             */
            if (saveFailure != null) {
                assertThat(saveFailure)
                        .isInstanceOf(
                                BusinessException.class
                        );

                assertThat(
                        ((BusinessException) saveFailure)
                                .getErrorCode()
                ).isEqualTo(
                        ErrorCode.AUTH_TOKEN_REVOKED
                );
            }

            assertThat(
                    authSessionStore.findBySessionId(
                            SESSION_ID
                    )
            ).isEmpty();

            assertThat(
                    redisTemplate.hasKey(
                            SESSION_KEY
                    )
            ).isFalse();

            assertThat(
                    redisTemplate.hasKey(
                            USER_SESSION_INDEX_KEY
                    )
            ).isFalse();

            assertThat(
                    redisTemplate
                            .opsForValue()
                            .get(
                                    USER_INVALIDATED_AT_KEY
                            )
            ).isEqualTo(
                    Long.toString(
                            invalidatedAt.toEpochMilli()
                    )
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName(
            "전체 로그아웃과 Refresh Token Rotation이 동시에 실행되어도 "
                    + "기존 인증 세션은 남지 않는다"
    )
    void logoutAll_concurrentRotationDoesNotLeaveSession()
            throws Exception {
        // given
        AuthSession session =
                createSession();

        authSessionStore.save(
                session
        );

        Instant invalidatedAt =
                NOW.plusMillis(1);

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            Future<?> logoutAllFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        authSessionLogoutAllStore.logoutAll(
                                USER_ID,
                                invalidatedAt
                        );

                        return null;
                    });

            Future<AuthSessionRotationResult> rotationFuture =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return authSessionStore.rotateRefreshToken(
                                SESSION_ID,
                                USER_ID,
                                session.createdAt(),
                                ORIGINAL_REFRESH_TOKEN_HASH,
                                ROTATED_REFRESH_TOKEN_HASH
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

            logoutAllFuture.get(
                    5,
                    TimeUnit.SECONDS
            );

            AuthSessionRotationResult rotationResult =
                    rotationFuture.get(
                            5,
                            TimeUnit.SECONDS
                    );

            // then
            /*
             * Rotation이 먼저 실행된 경우:
             * ROTATED 후 logoutAll이 세션을 삭제합니다.
             *
             * logoutAll이 먼저 실행된 경우:
             * 세션이 이미 삭제되어 SESSION_NOT_FOUND를 반환합니다.
             */
            assertThat(rotationResult)
                    .isIn(
                            AuthSessionRotationResult.ROTATED,
                            AuthSessionRotationResult.SESSION_NOT_FOUND
                    );

            assertThat(
                    authSessionStore.findBySessionId(
                            SESSION_ID
                    )
            ).isEmpty();

            assertThat(
                    redisTemplate.hasKey(
                            SESSION_KEY
                    )
            ).isFalse();

            assertThat(
                    redisTemplate.hasKey(
                            USER_SESSION_INDEX_KEY
                    )
            ).isFalse();

            assertThat(
                    redisTemplate
                            .opsForValue()
                            .get(
                                    USER_INVALIDATED_AT_KEY
                            )
            ).isEqualTo(
                    Long.toString(
                            invalidatedAt.toEpochMilli()
                    )
            );
        } finally {
            executorService.shutdownNow();
        }
    }
}
