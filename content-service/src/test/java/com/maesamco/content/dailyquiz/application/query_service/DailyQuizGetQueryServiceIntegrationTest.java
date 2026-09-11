package com.maesamco.content.dailyquiz.application.query_service;

import com.maesamco.content.dailyquiz.application.query.DailyQuizGetQuery;
import com.maesamco.content.dailyquiz.application.result.DailyQuizGetResult;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptStatus;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptRepository;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import com.maesamco.content.dailyquiz.infrastructure.persistence.DailyQuizAttemptItemRepositoryImpl;
import com.maesamco.content.dailyquiz.infrastructure.persistence.DailyQuizAttemptRepositoryImpl;
import com.maesamco.content.dailyquiz.infrastructure.persistence.DailyQuizQuestionRepositoryImpl;
import com.maesamco.content.global.config.JpaAuditingConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.maesamco.content.dailyquiz.domain.entity.DailyQuizProblemType.SHORT_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostgreSQL에서 오늘의 Daily Quiz 조회와 최초 상태 전이를 검증합니다.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        DailyQuizGetQueryService.class,
        DailyQuizAttemptRepositoryImpl.class,
        DailyQuizAttemptItemRepositoryImpl.class,
        DailyQuizQuestionRepositoryImpl.class,
        JpaAuditingConfig.class,
        DailyQuizGetQueryServiceIntegrationTest.FixedClockConfig.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DailyQuizGetQueryServiceIntegrationTest {

    // 테스트 결과를 실행 시각과 무관하게 동일하게 검증하기 위한 테스트 전용 기준 시각입니다.
    // 운영 환경에서는 설정된 Clock이 실제 현재 시각을 반환하므로 이 값이 사용되지 않습니다.
    private static final Instant TEST_NOW = Instant.parse("2026-09-11T03:00:00Z");
    private static final ZoneId QUIZ_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalDate ATTEMPT_DATE = LocalDate.of(2026, 9, 11);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private DailyQuizGetQueryService queryService;

    @Autowired
    private DailyQuizAttemptRepository attemptRepository;

    @Autowired
    private DailyQuizAttemptItemRepository attemptItemRepository;

    @Autowired
    private DailyQuizQuestionRepository questionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MutableClock dailyQuizClock;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        MutableClock dailyQuizClock() {
            return new MutableClock(TEST_NOW, QUIZ_ZONE_ID);
        }

        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    static class MutableClock extends Clock {

        private volatile Instant currentInstant;
        private final ZoneId zoneId;

        MutableClock(Instant currentInstant, ZoneId zoneId) {
            this.currentInstant = currentInstant;
            this.zoneId = zoneId;
        }

        void setInstant(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    @BeforeEach
    void resetClock() {
        // 이전 테스트에서 변경한 시간을 다음 테스트에 영향을 주지 않도록 기준 시각으로 초기화합니다.
        dailyQuizClock.setInstant(TEST_NOW);
    }

    @Test
    void READY_세트를_최초_조회하면_실제_DB에서_IN_PROGRESS로_전환한다() {
        UUID userId = UUID.randomUUID();
        // DailyQuizQustion 3개 -> Ready상태 1개의 센트 완성
        DailyQuizAttempt savedAttempt = createReadySet(userId, List.of(1, 2, 3));

        // 조회 API 호출
        DailyQuizGetResult result = queryService.get(DailyQuizGetQuery.from(userId));

        // 호출 후 DB에서 Attmpt 조회
        DailyQuizAttempt reloadedAttempt = attemptRepository
                .findByUserIdAndAttemptDate(userId, ATTEMPT_DATE)
                .orElseThrow();

        assertThat(result.quizAttemptId()).isEqualTo(savedAttempt.getId());
        assertThat(result.attemptStatus()).isEqualTo(DailyQuizAttemptStatus.IN_PROGRESS);
        assertThat(result.startedAt()).isEqualTo(TEST_NOW);
        assertThat(result.questions()).hasSize(3);
        assertThat(reloadedAttempt.getStatus()).isEqualTo(DailyQuizAttemptStatus.IN_PROGRESS);
        assertThat(reloadedAttempt.getStartedAt()).isEqualTo(TEST_NOW);
    }

    @Test
    void IN_PROGRESS_세트를_반복_조회해도_최초_startedAt을_유지한다() {
        UUID userId = UUID.randomUUID();
        createReadySet(userId, List.of(1, 2, 3));
        DailyQuizGetResult firstResult = queryService.get(DailyQuizGetQuery.from(userId));

        dailyQuizClock.setInstant(TEST_NOW.plus(Duration.ofMinutes(5)));
        DailyQuizGetResult secondResult = queryService.get(DailyQuizGetQuery.from(userId));

        DailyQuizAttempt reloadedAttempt = attemptRepository
                .findByUserIdAndAttemptDate(userId, ATTEMPT_DATE)
                .orElseThrow();
        assertThat(firstResult.startedAt()).isEqualTo(TEST_NOW);
        assertThat(secondResult.startedAt()).isEqualTo(TEST_NOW);
        assertThat(reloadedAttempt.getStartedAt()).isEqualTo(TEST_NOW);
    }

    @Test
    void 배정_문항을_questionOrder_오름차순으로_반환한다() {
        UUID userId = UUID.randomUUID();
        createReadySet(userId, List.of(3, 1, 2));

        DailyQuizGetResult result = queryService.get(DailyQuizGetQuery.from(userId));

        assertThat(result.questions())
                .extracting(question -> question.questionOrder())
                .containsExactly(1, 2, 3);
    }

    @Test
    void 두_조회_요청이_동시에_실행되어도_같은_startedAt을_반환한다() throws Exception {
        UUID userId = UUID.randomUUID();
        createReadySet(userId, List.of(1, 2, 3));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // [동시성 테스트 준비] 두 워커 스레드가 "정확히 같은 순간"에 조회 로직을 실행하도록
        // CountDownLatch 2개로 타이밍을 맞춘다.

        // ready: 메인 스레드가 "두 워커 스레드 모두 출발선에 도착했는지" 확인하기 위한 신호 수집용 카운터.
        // - 워커스레드1, 워커스레드2가 각자 대기 지점(start.await() 직전)에 도착하면 countDown()으로 "도착 신호"를 1번씩 보냄
        // - 총 2번의 도착 신호가 모여야(카운트 0) 메인 스레드의 ready.await()가 풀림
        // - 즉, "둘 다 준비 완료됐다"는 걸 메인 스레드가 확인하는 용도
        CountDownLatch ready = new CountDownLatch(2);
        // start: 메인 스레드가 두 워커 스레드에게 동시에 "출발!" 신호를 보내는 스위치.
        // - ready로 둘 다 준비된 걸 확인한 뒤, 메인 스레드가 countDown()을 딱 1번 호출
        // - 그 순간 대기 중이던 워커스레드1, 워커스레드2의 start.await()가 동시에 풀리며
        //   실제 조회 로직이 같은 타이밍에 실행됨
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<DailyQuizGetResult> first = submitConcurrentGet(executor, ready, start, userId);
            Future<DailyQuizGetResult> second = submitConcurrentGet(executor, ready, start, userId);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            // 여기서 동시 실행
            start.countDown();

            DailyQuizGetResult firstResult = first.get(10, TimeUnit.SECONDS);
            DailyQuizGetResult secondResult = second.get(10, TimeUnit.SECONDS);
            DailyQuizAttempt reloadedAttempt = attemptRepository
                    .findByUserIdAndAttemptDate(userId, ATTEMPT_DATE)
                    .orElseThrow();

            assertThat(firstResult.attemptStatus()).isEqualTo(DailyQuizAttemptStatus.IN_PROGRESS);
            assertThat(secondResult.attemptStatus()).isEqualTo(DailyQuizAttemptStatus.IN_PROGRESS);
            assertThat(firstResult.startedAt()).isEqualTo(TEST_NOW);
            assertThat(secondResult.startedAt()).isEqualTo(TEST_NOW);
            assertThat(reloadedAttempt.getStartedAt()).isEqualTo(TEST_NOW);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 서로_다른_트랜잭션의_조건부_시작_UPDATE는_한_건만_성공한다() throws Exception {
        DailyQuizAttempt savedAttempt = attemptRepository.save(
                DailyQuizAttempt.createReady(UUID.randomUUID(), ATTEMPT_DATE, 3)
        );
        // 다른 시작 시각 설정
        Instant firstStartedAt = TEST_NOW;
        Instant secondStartedAt = TEST_NOW.plusSeconds(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            // 작업 제출
            Future<Integer> first = submitConcurrentStart(executor, ready, start, savedAttempt.getId(), firstStartedAt);
            Future<Integer> second = submitConcurrentStart(executor, ready, start, savedAttempt.getId(), secondStartedAt);

            // 동시 실행 트리거
            // 둘 다 대기 지점 도착을 확인
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            // 동시 출발
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(1, 0);

            DailyQuizAttempt reloadedAttempt = attemptRepository
                    .findByUserIdAndAttemptDate(savedAttempt.getUserId(), ATTEMPT_DATE)
                    .orElseThrow();
            assertThat(reloadedAttempt.getStatus()).isEqualTo(DailyQuizAttemptStatus.IN_PROGRESS);
            assertThat(reloadedAttempt.getStartedAt())
                    .isIn(firstStartedAt, secondStartedAt);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private DailyQuizAttempt createReadySet(UUID userId, List<Integer> insertionOrder) {
        DailyQuizAttempt savedAttempt = attemptRepository.save(
                DailyQuizAttempt.createReady(userId, ATTEMPT_DATE, insertionOrder.size())
        );
        for (int questionOrder : insertionOrder) {
            DailyQuizQuestion question = questionRepository.save(
                    DailyQuizQuestion.createNew(
                            SHORT_ANSWER,
                            "테스트 문제 " + questionOrder,
                            null,
                            "정답 " + questionOrder,
                            null,
                            List.of("테스트 개념")
                    )
            );
            attemptItemRepository.save(
                    DailyQuizAttemptItem.assign(
                            savedAttempt.getId(),
                            question.getId(),
                            questionOrder,
                            insertionOrder.size()
                    )
            );
        }
        return savedAttempt;
    }

    private Future<DailyQuizGetResult> submitConcurrentGet(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            UUID userId
    ) {
        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 조회 시작 신호를 기다리지 못했습니다.");
            }
            return queryService.get(DailyQuizGetQuery.from(userId));
        });
    }

    private Future<Integer> submitConcurrentStart(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            UUID attemptId,
            Instant startedAt
    ) {
        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 UPDATE 시작 신호를 기다리지 못했습니다.");
            }
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            return transactionTemplate.execute(
                    status -> attemptRepository.startIfReady(attemptId, startedAt)
            );
        });
    }
}
