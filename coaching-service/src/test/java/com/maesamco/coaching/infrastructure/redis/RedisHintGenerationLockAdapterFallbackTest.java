package com.maesamco.coaching.infrastructure.redis;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

/**
 * Redis 장애 시 fail-open 카운터(이슈 #74)가 실제로 증가하는지 검증한다.
 *
 * {@link RedisHintGenerationLockAdapterTest}는 실제 Redis(Testcontainers)로 락
 * 획득/해제 자체의 정합성을 검증하는 반면, 이 클래스는 Redis 호출이 예외를 던지는
 * 상황을 Mockito로 인위적으로 만들어서 fail-open 시 관측 지표가 정확히 남는지만
 * 확인한다 — 목적이 다른 두 테스트를 분리해서 각자 가장 적합한 도구를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class RedisHintGenerationLockAdapterFallbackTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private RedisHintGenerationLockAdapter lockAdapter;

    @BeforeEach
    void setUp() {
        lockAdapter = new RedisHintGenerationLockAdapter(redisTemplate, meterRegistry);
    }

    private double fallbackCount(String operation) {
        return meterRegistry.counter("hint.generation.lock.fallback", "operation", operation).count();
    }

    @Nested
    @DisplayName("tryLock")
    class TryLock {

        @Test
        @DisplayName("Redis 호출이 예외를 던지면 락 없이 통과시키고 try_lock 카운터를 증가시킨다")
        void incrementsTryLockCounter_andReturnsTrue_whenRedisThrows() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .willThrow(new QueryTimeoutException("Redis 응답 없음"));

            boolean acquired = lockAdapter.tryLock(UUID.randomUUID(), "token-a");

            assertThat(acquired).isTrue();
            assertThat(fallbackCount("try_lock")).isEqualTo(1.0);
            assertThat(fallbackCount("unlock")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("unlock")
    class Unlock {

        @Test
        @DisplayName("Redis 호출이 예외를 던지면 조용히 넘어가고 unlock 카운터를 증가시킨다")
        void incrementsUnlockCounter_whenRedisThrows() {
            doThrow(new QueryTimeoutException("Redis 응답 없음"))
                    .when(redisTemplate).execute(any(), any(), any());

            lockAdapter.unlock(UUID.randomUUID(), "token-a");

            assertThat(fallbackCount("unlock")).isEqualTo(1.0);
            assertThat(fallbackCount("try_lock")).isEqualTo(0.0);
        }
    }
}
