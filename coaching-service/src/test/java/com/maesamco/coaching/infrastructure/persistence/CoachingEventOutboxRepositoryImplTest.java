package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PR #123 재검토 2차(용현님, 2026-09-10) — "재조회 후 status 확인"만으로는 진짜 동시 실행
 * (두 트랜잭션이 거의 동시에 같은 행을 PENDING으로 읽는 경우)까지 막지 못한다는 지적에 대한
 * 실제 검증. `ProblemAllTest.saveAndFlush_withStaleEntity_throwsOptimisticLockingFailure()`
 * (content-service)와 동일한 기법 — 실제 스레드를 띄우는 대신, 같은 행을 각각 detach한 두
 * 인스턴스로 재현한다(둘 다 같은 version을 들고 있다가 하나만 먼저 flush에 성공하는 상황과
 * 동등하다).
 *
 * 이슈 #261 — {@code claimPublishable()}은 SELECT(FOR UPDATE)와 flush()가 같은 트랜잭션
 * 안에서 원자적으로 일어나야 잠금이 의미가 있다. 이 테스트 클래스의 다른 테스트들처럼
 * {@code CoachingEventOutboxRepositoryImpl}을 직접 {@code new}로 만들면 그 위의
 * {@code @Transactional}이 스프링 프록시를 안 거쳐서 적용되지 않으므로, {@code @Import}로
 * 실제 스프링 빈으로 등록해서 주입받는다(content-service DailyQuizEventOutboxRepositoryTest와
 * 동일한 이유).
 */
@Import(CoachingEventOutboxRepositoryImpl.class)
class CoachingEventOutboxRepositoryImplTest extends AbstractCoachingRepositoryTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Instant AVAILABLE_AT = Instant.parse("2026-09-17T01:00:00Z");
    private static final Instant LEASE_UNTIL = Instant.parse("2026-09-17T01:05:00Z");

    @Autowired
    private SpringDataCoachingEventOutboxRepository springDataCoachingEventOutboxRepository;

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CoachingEventOutboxRepositoryImpl coachingEventOutboxRepository;

    private UUID createCoachingSession() {
        CoachingSession session = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
        return springDataCoachingSessionRepository.save(session).getId();
    }

    private ObjectNode payload() {
        return JSON_MAPPER.createObjectNode().put("coachingId", UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("Outbox를 저장하면 ID가 채번되고 version 0·PENDING 상태로 시작한다")
    void save_assignsIdAndDefaults() {
        // given
        CoachingEventOutbox outbox = CoachingEventOutbox.create(createCoachingSession(), "CoachingCompleted", payload());

        // when
        CoachingEventOutbox saved = coachingEventOutboxRepository.save(outbox);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    @DisplayName("오래된 version을 가진 인스턴스를 저장하면 실제 PostgreSQL에서 낙관적 락 충돌이 발생한다 "
            + "— 두 Relay 인스턴스가 거의 동시에 같은 Outbox를 처리하는 상황과 동등하다")
    void save_withStaleVersion_throwsOptimisticLockingFailure() {
        // given
        UUID outboxId = coachingEventOutboxRepository.save(
                CoachingEventOutbox.create(createCoachingSession(), "CoachingCompleted", payload())
        ).getId();
        entityManager.clear();

        // 같은 행을 각각 version 0 상태로 조회하고 영속성 컨텍스트에서 분리한다 — 두 Relay
        // 인스턴스가 거의 동시에 findById()로 같은 PENDING 행을 읽은 상황을 재현한다.
        CoachingEventOutbox firstOutbox = springDataCoachingEventOutboxRepository.findById(outboxId).orElseThrow();
        entityManager.detach(firstOutbox);

        CoachingEventOutbox secondOutbox = springDataCoachingEventOutboxRepository.findById(outboxId).orElseThrow();
        entityManager.detach(secondOutbox);

        assertThat(firstOutbox.getVersion()).isZero();
        assertThat(secondOutbox.getVersion()).isZero();

        // 첫 번째 Relay 인스턴스가 먼저 완료 처리한다.
        firstOutbox.incrementAttemptCount();
        firstOutbox.markPublished();
        CoachingEventOutbox firstSaved = coachingEventOutboxRepository.save(firstOutbox);

        assertThat(firstSaved.getVersion()).isEqualTo(1L);
        entityManager.clear();

        // 두 번째 인스턴스는 아직 version 0을 들고 있다 — 이미 COMPLETED로 끝난 행을 실패로
        // 되돌리려는 시도.
        secondOutbox.incrementAttemptCount();
        secondOutbox.scheduleNextAttempt();

        // when & then
        assertThatThrownBy(() -> coachingEventOutboxRepository.save(secondOutbox))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("next_attempt_at이 아직 지나지 않은 PENDING 행은 선점 대상에서 제외된다 "
            + "— head-of-line blocking 방지(PR #123 재검토 2차)")
    void claimPublishable_excludesRowsWaitingForBackoff() {
        // given
        CoachingEventOutbox stillBackingOff =
                coachingEventOutboxRepository.save(
                        CoachingEventOutbox.create(createCoachingSession(), "CoachingCompleted", payload())
                );
        stillBackingOff.incrementAttemptCount();
        stillBackingOff.scheduleNextAttempt(); // 다음 재시도 가능 시각이 미래로 잡힘
        coachingEventOutboxRepository.save(stillBackingOff);

        CoachingEventOutbox readyNow =
                coachingEventOutboxRepository.save(
                        CoachingEventOutbox.create(createCoachingSession(), "CoachingCompleted", payload())
                ); // next_attempt_at이 null이라 즉시 선점 대상

        // when
        List<CoachingEventOutbox> claimed = coachingEventOutboxRepository.claimPublishable(
                AVAILABLE_AT, LEASE_UNTIL, UUID.randomUUID(), 100);

        // then
        assertThat(claimed)
                .extracting(CoachingEventOutbox::getId)
                .contains(readyNow.getId())
                .doesNotContain(stillBackingOff.getId());
    }

    @Test
    @DisplayName("next_attempt_at이 이미 지난 PENDING 행은 다시 선점 대상에 포함된다")
    void claimPublishable_includesRowsPastBackoffWindow() {
        // given
        CoachingEventOutbox outbox = coachingEventOutboxRepository.save(
                CoachingEventOutbox.create(createCoachingSession(), "CoachingCompleted", payload())
        );
        outbox.incrementAttemptCount();
        outbox.scheduleNextAttempt();
        coachingEventOutboxRepository.save(outbox);

        // 백오프 창을 이미 지난 것처럼 직접 과거로 되돌린다(실제 backoff 시간을 기다리지 않기 위함).
        // AVAILABLE_AT(claimPublishable에 넘길 선점 시각) 기준으로 과거여야 한다 — Instant.now()를
        // 쓰면 AVAILABLE_AT이 고정된 과거 상수라서 비교가 어긋난다.
        entityManager.createQuery(
                        "UPDATE CoachingEventOutbox o SET o.nextAttemptAt = :past WHERE o.id = :id")
                .setParameter("past", AVAILABLE_AT.minusSeconds(60))
                .setParameter("id", outbox.getId())
                .executeUpdate();
        entityManager.clear();

        // when
        List<CoachingEventOutbox> claimed = coachingEventOutboxRepository.claimPublishable(
                AVAILABLE_AT, LEASE_UNTIL, UUID.randomUUID(), 100);

        // then
        assertThat(claimed).extracting(CoachingEventOutbox::getId).contains(outbox.getId());
    }

    @Test
    @DisplayName("선점에 성공하면 IN_PROGRESS로 전이하고 claim_id·lease_until을 기록한다")
    void claimPublishable_transitionsToInProgressWithClaimInfo() {
        CoachingEventOutbox saved = coachingEventOutboxRepository.save(
                CoachingEventOutbox.create(createCoachingSession(), "CoachingCompleted", payload())
        );
        UUID claimId = UUID.randomUUID();

        List<CoachingEventOutbox> claimed = coachingEventOutboxRepository.claimPublishable(
                AVAILABLE_AT, LEASE_UNTIL, claimId, 100);

        assertThat(claimed).singleElement().satisfies(outbox -> {
            assertThat(outbox.getId()).isEqualTo(saved.getId());
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
            assertThat(outbox.getClaimId()).isEqualTo(claimId);
            assertThat(outbox.getLeaseUntil()).isEqualTo(LEASE_UNTIL);
        });
    }

    @Test
    @DisplayName("lease가 만료된 IN_PROGRESS 행은 새로운 Worker가 재선점한다")
    void claimPublishable_reclaimsExpiredLease() {
        CoachingEventOutbox saved = coachingEventOutboxRepository.save(
                CoachingEventOutbox.create(createCoachingSession(), "CoachingCompleted", payload())
        );
        UUID firstClaimId = UUID.randomUUID();
        UUID secondClaimId = UUID.randomUUID();

        coachingEventOutboxRepository.claimPublishable(AVAILABLE_AT, LEASE_UNTIL, firstClaimId, 1);
        entityManager.clear();

        List<CoachingEventOutbox> beforeExpiration = coachingEventOutboxRepository.claimPublishable(
                LEASE_UNTIL.minusMillis(1), LEASE_UNTIL.plusSeconds(60), secondClaimId, 1);
        List<CoachingEventOutbox> afterExpiration = coachingEventOutboxRepository.claimPublishable(
                LEASE_UNTIL, LEASE_UNTIL.plusSeconds(60), secondClaimId, 1);

        assertThat(beforeExpiration).isEmpty();
        assertThat(afterExpiration).singleElement().satisfies(outbox -> {
            assertThat(outbox.getId()).isEqualTo(saved.getId());
            assertThat(outbox.getClaimId()).isEqualTo(secondClaimId);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("두 Relay Worker가 동시에 선점해도 동일 Outbox는 한 Worker만 가져간다")
    void claimPublishable_allowsOnlyOneWorkerToClaimSameOutbox() throws Exception {
        jdbcTemplate.update("DELETE FROM coaching_schema.p_coaching_event_outboxes");
        UUID sessionId = createCoachingSession();
        CoachingEventOutbox saved =
                coachingEventOutboxRepository.save(CoachingEventOutbox.create(sessionId, "CoachingCompleted", payload()));
        UUID firstClaimId = UUID.randomUUID();
        UUID secondClaimId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<CoachingEventOutbox>> first = executor.submit(() -> {
                start.await();
                return coachingEventOutboxRepository.claimPublishable(AVAILABLE_AT, LEASE_UNTIL, firstClaimId, 1);
            });
            Future<List<CoachingEventOutbox>> second = executor.submit(() -> {
                start.await();
                return coachingEventOutboxRepository.claimPublishable(AVAILABLE_AT, LEASE_UNTIL, secondClaimId, 1);
            });

            start.countDown();

            List<CoachingEventOutbox> firstResult = first.get();
            List<CoachingEventOutbox> secondResult = second.get();

            assertThat(firstResult.size() + secondResult.size()).isEqualTo(1);
            assertThat(firstResult.isEmpty() ? secondResult : firstResult)
                    .singleElement()
                    .extracting(CoachingEventOutbox::getId)
                    .isEqualTo(saved.getId());

            CoachingEventOutbox found = coachingEventOutboxRepository.findById(saved.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
            assertThat(found.getClaimId()).isIn(firstClaimId, secondClaimId);
        } finally {
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM coaching_schema.p_coaching_event_outboxes");
        }
    }
}
