package com.maesamco.coaching.infrastructure.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mockito로는 실제 Redis의 SETNX/TTL/Lua 스크립트 동작을 검증할 수 없다 — 실제 Redis
 * 컨테이너로 락 획득·해제·compare-and-delete가 의도대로 동작하는지 직접 확인한다
 * (PR #70 리뷰, 비용/어뷰징 관점 — 동시 힌트 요청의 LLM 중복 호출 방지 락).
 */
class RedisHintGenerationLockAdapterTest {

    private static GenericContainer<?> redisContainer;
    private static RedisHintGenerationLockAdapter lockAdapter;
    private static LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7"))
                .withExposedPorts(6379);
        redisContainer.start();

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)
        );
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        lockAdapter = new RedisHintGenerationLockAdapter(redisTemplate);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    private final UUID coachingSessionId = UUID.randomUUID();

    @BeforeEach
    void cleanUpAnyLeftoverLock() {
        lockAdapter.unlock(coachingSessionId, "cleanup-force"); // 실패해도 무해(compare-and-delete)
    }

    @Test
    @DisplayName("같은 세션에 대해 두 번째 tryLock은 실패한다")
    void secondTryLock_forSameSession_fails() {
        boolean first = lockAdapter.tryLock(coachingSessionId, "token-a");
        boolean second = lockAdapter.tryLock(coachingSessionId, "token-b");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("unlock 후에는 다시 tryLock에 성공한다")
    void tryLock_succeedsAgain_afterUnlock() {
        lockAdapter.tryLock(coachingSessionId, "token-a");
        lockAdapter.unlock(coachingSessionId, "token-a");

        boolean reacquired = lockAdapter.tryLock(coachingSessionId, "token-b");

        assertThat(reacquired).isTrue();
    }

    @Test
    @DisplayName("다른 토큰으로 unlock을 시도하면 실제 락 보유자의 락을 지우지 못한다(compare-and-delete)")
    void unlock_withWrongToken_doesNotReleaseSomeoneElsesLock() {
        lockAdapter.tryLock(coachingSessionId, "real-owner-token");

        lockAdapter.unlock(coachingSessionId, "wrong-token");

        boolean stillLocked = lockAdapter.tryLock(coachingSessionId, "attacker-token");
        assertThat(stillLocked).isFalse();
    }

    @Test
    @DisplayName("서로 다른 세션의 락은 서로 영향을 주지 않는다")
    void differentSessions_haveIndependentLocks() {
        UUID otherSessionId = UUID.randomUUID();

        boolean lockedFirst = lockAdapter.tryLock(coachingSessionId, "token-a");
        boolean lockedSecond = lockAdapter.tryLock(otherSessionId, "token-b");

        assertThat(lockedFirst).isTrue();
        assertThat(lockedSecond).isTrue();

        lockAdapter.unlock(otherSessionId, "token-b");
    }
}
