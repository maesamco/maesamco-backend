package com.maesamco.coaching.infrastructure.redis;

import com.maesamco.coaching.application.port.AiFeedbackRetryLockPort;
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
 * AiFeedbackRetryLockPort의 Redis 구현체 — RedisHintGenerationLockAdapter와 동일한 이유로
 * DB 비관적 락 대신 Redis를 쓴다(LLM 호출 동안 DB 커넥션을 쥐고 있지 않기 위해, 팀 컨벤션
 * 2절). Redis 장애 시에는 락 없이 그냥 진행한다(fail-open) — 이 락도 비용/남용 보호용
 * 부가 장치라, Redis가 죽었다고 재시도 핵심 기능까지 막을 이유는 없다.
 *
 * TTL을 30초가 아니라 240초로 넉넉히 잡은 이유: 이 락이 감싸는 구간(재시도 카운트 체크 ~
 * Judge Service 조회 ~ LLM 호출 ~ 이력 저장)은 힌트 생성 하나보다 훨씬 길다 — Judge Service
 * Feign 호출에 타임아웃 설정이 없어 기본값(연결 10초/읽기 60초, 최악 70초)이 그대로 적용된다.
 *
 * 재검증(PR #111) — 애초 "최대 재시도 2회 × 타임아웃 30초로 최악 약 60초"라던 계산이
 * 틀렸었다. Anthropic Java SDK 소스(RetryingHttpClient.kt) 확인 결과 spring.ai.anthropic
 * .max-retries=2는 "최초 1회 + 재시도 2회"라 실제로는 최대 3회 시도가 나갈 수 있고, 시도당
 * 타임아웃(30초)이 매번 새로 적용된다 — 지수 백오프(회당 최대 8초)까지 더하면 LLM 쪽만으로도
 * 최악 약 90~100초가 걸린다. 여기에 Judge Service 70초를 더하면 최악의 경우 약 160~170초로,
 * 기존 TTL(150초)보다 실제로 더 길 수 있었다(락이 먼저 풀려 보호 목적이 무의미해지는 상황).
 * 240초로 올려 여유를 두되, 재시도/타임아웃 설정값이 나중에 또 바뀔 수 있으므로 이 숫자를
 * 다시 계산해야 한다는 걸 여기 남겨둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAiFeedbackRetryLockAdapter implements AiFeedbackRetryLockPort {

    private static final Duration LOCK_TTL = Duration.ofSeconds(240);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> unlockScript = RedisScript.of(
            new ClassPathResource("scripts/feedback_retry_lock_unlock.lua"), Long.class);

    @Override
    public boolean tryLock(UUID coachingSessionId, String lockToken) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(coachingSessionId), lockToken, LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException e) {
            log.warn("AI 피드백 재시도 락 획득 실패(Redis 오류) - coachingSessionId={}, 락 없이 진행합니다.", coachingSessionId, e);
            return true;
        }
    }

    @Override
    public void unlock(UUID coachingSessionId, String lockToken) {
        try {
            redisTemplate.execute(unlockScript, List.of(key(coachingSessionId)), lockToken);
        } catch (RuntimeException e) {
            // 못 지워도 TTL이 있어서 언젠가 자연 만료된다 — 핵심 기능을 막을 이유는 없다.
            log.warn("AI 피드백 재시도 락 해제 실패(Redis 오류) - coachingSessionId={}, TTL로 자연 만료됩니다.", coachingSessionId, e);
        }
    }

    private String key(UUID coachingSessionId) {
        return "coaching:feedback-retry-lock:" + coachingSessionId;
    }
}
