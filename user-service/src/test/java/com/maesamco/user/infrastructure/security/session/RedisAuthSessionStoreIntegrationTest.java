package com.maesamco.user.infrastructure.security.session;

import com.maesamco.user.application.port.AuthSession;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis를 이용해 인증 세션 저장과 TTL을 검증합니다.
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
                .contains("refresh-token-hash");
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
                "refresh-token-hash",
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
