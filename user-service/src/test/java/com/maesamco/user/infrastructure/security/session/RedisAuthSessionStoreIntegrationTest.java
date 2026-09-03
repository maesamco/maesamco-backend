package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSession;
import com.maesamco.user.application.port.AuthSessionRotationResult;
import com.maesamco.user.application.port.AuthSessionStore;
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

/**
 * 실제 Redis를 이용해 인증 세션 저장, TTL,
 * Refresh Token Rotation 및 Reuse Detection을 검증합니다.
 */
@DataRedisTest
@Testcontainers
@Import({
        RedisAuthSessionStore.class,
        RedisAuthSessionStoreIntegrationTest.TestConfig.class
})
class RedisAuthSessionStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final Instant NOW =
            Instant.parse("2026-09-02T00:00:00Z");

    private static final Duration SESSION_TTL =
            Duration.ofDays(14);

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

    private static final String SESSION_KEY =
            "session:" + SESSION_ID;

    private static final String ORIGINAL_REFRESH_TOKEN_HASH =
            "refresh-token-hash-a";

    private static final String ROTATED_REFRESH_TOKEN_HASH =
            "refresh-token-hash-b";

    private static final String SECOND_ROTATED_REFRESH_TOKEN_HASH =
            "refresh-token-hash-c";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7-alpine")
            ).withExposedPorts(REDIS_PORT);

    @Autowired
    private AuthSessionStore authSessionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Testcontainers Redis 접속 정보를 Spring에 등록합니다.
     */
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
        redisTemplate.delete(SESSION_KEY);
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
    @DisplayName("이미 Rotation된 이전 Refresh Token을 재사용하면 세션을 폐기한다")
    void rotateRefreshToken_reuseDeletesSession() {
        // given
        authSessionStore.save(createSession());

        AuthSessionRotationResult firstRotation =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
                        ORIGINAL_REFRESH_TOKEN_HASH,
                        ROTATED_REFRESH_TOKEN_HASH
                );

        assertThat(firstRotation)
                .isEqualTo(
                        AuthSessionRotationResult.ROTATED
                );

        // when
        AuthSessionRotationResult reuseResult =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
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
    @DisplayName("존재하지 않는 인증 세션을 Rotation하면 SESSION_NOT_FOUND를 반환한다")
    void rotateRefreshToken_missingSessionReturnsSessionNotFound() {
        // when
        AuthSessionRotationResult result =
                authSessionStore.rotateRefreshToken(
                        SESSION_ID,
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
    @DisplayName("같은 Refresh Token으로 동시에 Rotation하면 하나만 성공하고 재사용 요청이 세션을 폐기한다")
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
                            AuthSessionRotationResult.TOKEN_REUSED
                    );

            assertThat(
                    authSessionStore.findBySessionId(SESSION_ID)
            ).isEmpty();
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

    /**
     * 통합 테스트에 사용할 인증 세션을 생성합니다.
     */
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

    /**
     * 통합 테스트에서 사용할 고정 시계와 JSON Mapper를 구성합니다.
     */
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
        JsonMapper testJsonMapper() {
            return JsonMapper.builder()
                    .findAndAddModules()
                    .build();
        }
    }
}
