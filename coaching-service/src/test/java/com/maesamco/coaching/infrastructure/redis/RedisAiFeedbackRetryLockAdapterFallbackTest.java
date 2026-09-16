package com.maesamco.coaching.infrastructure.redis;

import io.micrometer.core.instrument.Counter;
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
 * RedisHintGenerationLockAdapterFallbackTest와 동일한 목적(PR #190 리뷰) — Redis 장애 시
 * fail-open 카운터가 실제로 증가하는지, 그리고 그 카운터가 생성자 시점에 이미 등록돼
 * 있는지(첫 fail-open 유실 방지, RedisHintGenerationLockAdapter 클래스 Javadoc 참고)를
 * Mockito로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RedisAiFeedbackRetryLockAdapterFallbackTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private RedisAiFeedbackRetryLockAdapter lockAdapter;

    @BeforeEach
    void setUp() {
        lockAdapter = new RedisAiFeedbackRetryLockAdapter(redisTemplate, meterRegistry);
    }

    private double fallbackCount(String operation) {
        return meterRegistry.counter("ai.feedback.retry.lock.fallback", "operation", operation).count();
    }

    @Test
    @DisplayName("생성자 호출 시점에 두 카운터 모두 이미 등록되어 있다(0 표본)")
    void bothCountersAreRegisteredEagerlyInConstructor() {
        assertThat(meterRegistry.find("ai.feedback.retry.lock.fallback").tag("operation", "try_lock").counter())
                .isNotNull()
                .extracting(Counter::count)
                .isEqualTo(0.0);
        assertThat(meterRegistry.find("ai.feedback.retry.lock.fallback").tag("operation", "unlock").counter())
                .isNotNull()
                .extracting(Counter::count)
                .isEqualTo(0.0);
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
