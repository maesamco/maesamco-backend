package com.maesamco.coaching.infrastructure.redis;

import com.maesamco.coaching.application.port.HintGenerationLockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * HintGenerationLockPort의 Redis 구현체.
 *
 * DB 비관적 락(SELECT ... FOR UPDATE)이 아니라 Redis를 쓰는 이유: 팀 컨벤션 2절이
 * "트랜잭션 안에서 외부 호출을 부르면 DB 커넥션을 쥔 채 네트워크를 기다리게 된다"고
 * 명시적으로 경고한다 — LLM 호출(수 초~십수 초) 동안 DB 락을 들고 있는 건 그 안티패턴
 * 그대로다. Redis 락은 DB 커넥션과 무관하게 걸 수 있다.
 *
 * Redis 장애 시에는 락 없이 그냥 진행한다(fail-open) — 이 락은 비용 보호용 부가 장치라,
 * Redis가 죽었다고 힌트 생성 핵심 기능까지 막을 이유는 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHintGenerationLockAdapter implements HintGenerationLockPort {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> unlockScript = RedisScript.of(
            new ClassPathResource("scripts/hint_lock_unlock.lua"), Long.class);

    @Override
    public boolean tryLock(UUID coachingSessionId, String lockToken) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(coachingSessionId), lockToken, LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException e) {
            log.warn("힌트 생성 락 획득 실패(Redis 오류) - coachingSessionId={}, 락 없이 진행합니다.", coachingSessionId, e);
            return true;
        }
    }

    @Override
    public void unlock(UUID coachingSessionId, String lockToken) {
        try {
            redisTemplate.execute(unlockScript, List.of(key(coachingSessionId)), lockToken);
        } catch (RuntimeException e) {
            // 못 지워도 TTL이 있어서 언젠가 자연 만료된다 — 핵심 기능을 막을 이유는 없다.
            log.warn("힌트 생성 락 해제 실패(Redis 오류) - coachingSessionId={}, TTL로 자연 만료됩니다.", coachingSessionId, e);
        }
    }

    private String key(UUID coachingSessionId) {
        return "coaching:hint-lock:" + coachingSessionId;
    }
}
