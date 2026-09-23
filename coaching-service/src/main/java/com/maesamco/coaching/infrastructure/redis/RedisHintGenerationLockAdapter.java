package com.maesamco.coaching.infrastructure.redis;

import com.maesamco.coaching.application.port.HintGenerationLockPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
 *
 * fail-open이 발생하면 Micrometer 카운터(이슈 #74)를 증가시켜 관측한다. tryLock
 * 실패(operation=try_lock)는 락 없이 진행되어 중복 LLM 호출이 실제로 가능해지는
 * 상태라 알림 대상이지만, unlock 실패(operation=unlock)는 TTL로 자연 만료될 뿐
 * 비용 리스크가 아니라서 같은 카운터에 태그로만 구분해 기록하고 알림 조건에서는
 * 제외한다(infra/grafana/provisioning/alerting/hint-lock-fallback-alert.yml 참고
 * — try_lock에만 Warning/Critical 2단계 임계치가 걸려있다).
 *
 * 실제 로컬 환경에서 붙여서 검증하다가 발견한 함정 — Micrometer는 카운터를 처음
 * increment()할 때에야 등록하므로, 딱 한 번뿐인 첫 fail-open은 Prometheus 입장에서
 * "0에서 시작해 1이 됨"이 아니라 "갑자기 1로 나타남"으로 보여서 increase()가 이를
 * 못 잡는다(두 번째 이후 실패부터만 정상 감지됨). 그래서 카운터를 increment 시점이
 * 아니라 생성자에서 미리 등록해, 앱 기동 시점부터 0이라는 표본이 항상 존재하게 한다.
 *
 * PR #190 리뷰 — LOCK_TTL을 30초에서 150초로 올렸다. tryLock()~unlock() 사이에서
 * Content Service Feign 조회(connectTimeout 2s + readTimeout 3s, 재시도 없음 — 최악
 * 약 5초)와 aiModelPort.generate()(LLM 호출)가 그대로 실행되는데, spring.ai.anthropic
 * .timeout=30s + max-retries=2(최초 1회 + 재시도 2회, RedisAiFeedbackRetryLockAdapter가
 * 이미 같은 방식으로 계산해둔 근거와 동일)에 지수 백오프까지 더하면 LLM 쪽만으로도 최악
 * 약 90~100초가 걸린다. 즉 기존 TTL(30초)로는 Redis가 멀쩡해도 응답이 느린 상황에서
 * 락이 자연 만료된 뒤 새 요청이 tryLock()에 성공해서 같은 stage에 대해 LLM을 한 번 더
 * 호출할 수 있었다 — 이 락이 원래 막으려던 문제(PR #70, 힌트 버튼 더블클릭·타임아웃
 * 재시도로 LLM 중복 과금 호출)가 Redis 오류 없이도 재현 가능한 상태였다. 이 경로는
 * RuntimeException을 던지지 않고 조용히 통과하므로 tryLockFallbackCounter로도 못 잡는다.
 * ~105초 worst-case에 여유를 둬서 150초로 올린다(재시도/타임아웃 설정이 나중에 바뀌면
 * 이 계산도 다시 해야 한다).
 *
 * 이슈 #207(해결됨) — TTL을 올리면서 waitForConcurrentHint()(HintGenerationFacade, 현재
 * 100ms×20회=2초 대기)와의 격차가 커졌던 문제는, 대기 시간을 늘리는 대신 에러코드를
 * 분리하는 방향(PR #210)으로 해결했다 — 대기창을 넘기면 실패가 아니라
 * HINT_GENERATION_IN_PROGRESS(409)로 응답해 "아직 진행 중이니 잠시 후 다시 시도"임을
 * 구분해서 알린다.
 */
@Slf4j
@Component
public class RedisHintGenerationLockAdapter implements HintGenerationLockPort {

    private static final Duration LOCK_TTL = Duration.ofSeconds(150);
    private static final String FALLBACK_METRIC_NAME = "hint.generation.lock.fallback";
    private static final String OPERATION_TAG = "operation";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> unlockScript = RedisScript.of(
            new ClassPathResource("scripts/hint_lock_unlock.lua"), Long.class);
    private final Counter tryLockFallbackCounter;
    private final Counter unlockFallbackCounter;

    public RedisHintGenerationLockAdapter(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.tryLockFallbackCounter = meterRegistry.counter(FALLBACK_METRIC_NAME, OPERATION_TAG, "try_lock");
        this.unlockFallbackCounter = meterRegistry.counter(FALLBACK_METRIC_NAME, OPERATION_TAG, "unlock");
    }

    @Override
    public boolean tryLock(UUID coachingSessionId, String lockToken) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(coachingSessionId), lockToken, LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException e) {
            tryLockFallbackCounter.increment();
            log.warn("힌트 생성 락 획득 실패(Redis 오류) - coachingSessionId={}, 락 없이 진행합니다.", coachingSessionId, e);
            return true;
        }
    }

    @Override
    public void unlock(UUID coachingSessionId, String lockToken) {
        try {
            redisTemplate.execute(unlockScript, List.of(key(coachingSessionId)), lockToken);
        } catch (RuntimeException e) {
            unlockFallbackCounter.increment();
            // 못 지워도 TTL이 있어서 언젠가 자연 만료된다 — 핵심 기능을 막을 이유는 없다.
            log.warn("힌트 생성 락 해제 실패(Redis 오류) - coachingSessionId={}, TTL로 자연 만료됩니다.", coachingSessionId, e);
        }
    }

    private String key(UUID coachingSessionId) {
        return "coaching:hint-lock:" + coachingSessionId;
    }
}
