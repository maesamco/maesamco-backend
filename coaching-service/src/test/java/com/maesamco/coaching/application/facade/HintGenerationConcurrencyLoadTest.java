package com.maesamco.coaching.application.facade;

import com.maesamco.coaching.application.CoachingSessionFinder;
import com.maesamco.coaching.application.persistence_service.WeakConceptPersistenceService;
import com.maesamco.coaching.application.port.AiModelPort;
import com.maesamco.coaching.application.port.AiModelResponse;
import com.maesamco.coaching.application.port.ContentServicePort;
import com.maesamco.coaching.application.port.JudgeServicePort;
import com.maesamco.coaching.application.port.ProblemSnapshot;
import com.maesamco.coaching.application.port.SubmissionSnapshot;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.Hint;
import com.maesamco.coaching.domain.repository.AiCallHistoryRepository;
import com.maesamco.coaching.domain.repository.CoachingSessionRepository;
import com.maesamco.coaching.domain.repository.HintRepository;
import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.infrastructure.redis.RedisHintGenerationLockAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 이슈 #207 실측 — {@code RedisHintGenerationLockAdapter}의 실제 Redis(Testcontainers)와
 * 실제 시간 흐름으로 동시 요청 시나리오를 재현한다. Mockito만으로는
 * {@code waitForConcurrentHint()}의 폴링 타이밍(100ms×20회=2초)이 LOCK_TTL(150초) 대비
 * 실제로 얼마나 짧은지 보여줄 수 없다 — {@code HintGenerationFacadeTest}의 기존
 * "동시 요청" 테스트들은 전부 락을 즉시 실패/성공으로 고정해두고 시간 요소 자체를
 * 검증하지 않는다.
 *
 * AI 호출은 3초 슬립 후 응답하는 페이크로 대체한다 — 실제 Claude/Gemini 호출(최악
 * 90~100초, LOCK_TTL 150초의 근거)을 그대로 재현하면 테스트가 비싸고 느려지므로,
 * "대기 창(2초)보다는 길고 TTL(150초)보다는 훨씬 짧다"는 조건만 만족하는 값으로
 * 축소했다 — 그래도 승자는 결국 성공할 시점(3초)에 나머지 요청들이 이미 2초 만에
 * 실패해버린다는 핵심 문제는 그대로 드러난다.
 */
class HintGenerationConcurrencyLoadTest {

    private static final Duration AI_CALL_DELAY = Duration.ofMillis(3000);
    private static final int CONCURRENT_REQUESTS = 10;

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisHintGenerationLockAdapter lockAdapter;

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
        lockAdapter = new RedisHintGenerationLockAdapter(redisTemplate, new SimpleMeterRegistry());
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

    /** 실제 DB 대신, 여러 스레드가 동시에 save/find해도 안전한 최소 인메모리 구현. */
    private static class InMemoryHintRepository implements HintRepository {
        private final Map<Integer, Hint> byStage = new ConcurrentHashMap<>();

        @Override
        public Hint save(Hint hint) {
            if (byStage.putIfAbsent(hint.getStage(), hint) != null) {
                throw new BusinessException(ErrorCode.HINT_ALREADY_EXISTS);
            }
            return hint;
        }

        @Override
        public List<Hint> findByCoachingSessionId(UUID coachingSessionId) {
            return List.copyOf(byStage.values());
        }

        @Override
        public Optional<Hint> findByCoachingSessionIdAndStage(UUID coachingSessionId, int stage) {
            return Optional.ofNullable(byStage.get(stage));
        }
    }

    private final UUID callerId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();
    private final UUID problemVersionId = UUID.randomUUID();
    private final int attemptNo = 1;

    private HintGenerationFacade facade;

    @BeforeEach
    void setUp() {
        JudgeServicePort judgeServicePort = mock(JudgeServicePort.class);
        ContentServicePort contentServicePort = mock(ContentServicePort.class);
        CoachingSessionRepository coachingSessionRepository = mock(CoachingSessionRepository.class);
        AiCallHistoryRepository aiCallHistoryRepository = mock(AiCallHistoryRepository.class);
        WeakConceptPersistenceService weakConceptPersistenceService = mock(WeakConceptPersistenceService.class);
        AiModelPort aiModelPort = mock(AiModelPort.class);

        CoachingSession session = CoachingSession.create(UUID.randomUUID(), callerId, problemId, attemptNo);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        when(coachingSessionRepository.findByUserIdAndProblemId(callerId, problemId))
                .thenReturn(Optional.of(session));

        when(judgeServicePort.getSubmission(any())).thenAnswer(inv -> new SubmissionSnapshot(
                inv.getArgument(0), callerId, problemId, problemVersionId,
                "public class Main {}", "WRONG", List.of(), attemptNo
        ));
        when(contentServicePort.getProblemVersion(any()))
                .thenReturn(new ProblemSnapshot(problemId, "문제 설명", List.of("재귀")));
        when(aiModelPort.generate(any(), any())).thenAnswer(inv -> {
            Thread.sleep(AI_CALL_DELAY.toMillis());
            return new AiModelResponse("1단계 힌트", "claude-sonnet-5", 42);
        });

        facade = new HintGenerationFacade(
                judgeServicePort, contentServicePort, new CoachingSessionFinder(coachingSessionRepository),
                new InMemoryHintRepository(), aiModelPort, aiCallHistoryRepository, lockAdapter,
                weakConceptPersistenceService, CircuitBreakerRegistry.ofDefaults()
        );
    }

    @Test
    void 동시_힌트_요청_10건_중_1건만_성공하고_나머지는_LOCK_TTL보다_훨씬_먼저_대기시간_초과로_실패한다() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();
        List<Long> failureElapsedMillis = new CopyOnWriteArrayList<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            UUID submissionId = UUID.randomUUID();
            pool.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    try {
                        facade.requestHint(submissionId, callerId);
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        // 이슈 #207 — 대기창을 못 채워서 포기한 경우는 진짜 실패
                        // (AI_GENERATION_FAILED)가 아니라 "아직 진행 중"으로 응답해야 한다.
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.HINT_GENERATION_IN_PROGRESS);
                        failureElapsedMillis.add((System.nanoTime() - start) / 1_000_000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long testStart = System.nanoTime();
        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(20, TimeUnit.SECONDS);
        long testElapsedMillis = (System.nanoTime() - testStart) / 1_000_000;
        pool.shutdown();

        assertThat(finishedInTime).as("20초 안에 모든 요청이 끝나야 한다").isTrue();
        assertThat(successCount.get()).as("락을 획득해 실제로 힌트를 생성한 요청은 1건뿐").isEqualTo(1);
        assertThat(failureElapsedMillis).hasSize(CONCURRENT_REQUESTS - 1);
        // waitForConcurrentHint()는 100ms×20회=2초 폴링 후 포기한다 — 넉넉한 오차(500ms)를 둔다.
        assertThat(failureElapsedMillis).as("나머지 요청은 전부 대기창(2초) 근처에서 실패해야 한다")
                .allSatisfy(elapsed -> assertThat(elapsed).isBetween(1900L, 2500L));
        // 핵심 증상: 승자는 3초 뒤 성공했을 텐데(AI_CALL_DELAY), 패자들은 그보다 먼저(2초)
        // 실패해버린다 — LOCK_TTL(150초)엔 한참 못 미치는데도 성공할 요청을 놓친다.
        assertThat(testElapsedMillis).as("테스트 전체는 승자의 AI 호출 시간(3초)만큼만 걸려야 한다(직렬화 아님)")
                .isLessThan(AI_CALL_DELAY.toMillis() + 2000);
    }
}
