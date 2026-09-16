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

    /**
     * PR #190 리뷰 대응 — MeterRegistry.counter(name, tags)는 "있으면 가져오고 없으면
     * 등록"하는 메서드라, fallbackCount()로 값을 읽는 행위 자체가 카운터를 등록해버릴 수
     * 있다. 즉 위 fallbackCount() 호출만으로는 "생성자에서 이미 등록돼 있었다"와 "이
     * assertion이 방금 처음 등록했다"를 구분하지 못한다 — 누군가 실수로 즉시 등록을
     * 지연 등록으로 되돌려도 이 헬퍼만으로는 안 잡힌다. meterRegistry.find(...).counter()
     * (등록 안 됐으면 null)로 생성자 호출 직후 실제 등록 여부를 먼저 증명한다.
     */
    @Test
    @DisplayName("생성자 호출 시점에 두 카운터 모두 이미 등록되어 있다(0 표본)")
    void bothCountersAreRegisteredEagerlyInConstructor() {
        assertThat(meterRegistry.find("hint.generation.lock.fallback").tag("operation", "try_lock").counter())
                .isNotNull()
                .extracting(io.micrometer.core.instrument.Counter::count)
                .isEqualTo(0.0);
        assertThat(meterRegistry.find("hint.generation.lock.fallback").tag("operation", "unlock").counter())
                .isNotNull()
                .extracting(io.micrometer.core.instrument.Counter::count)
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
