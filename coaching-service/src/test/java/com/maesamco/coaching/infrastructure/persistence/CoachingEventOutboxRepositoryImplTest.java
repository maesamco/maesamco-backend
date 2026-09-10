package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PR #123 재검토 2차(용현님, 2026-09-10) — "재조회 후 status 확인"만으로는 진짜 동시 실행
 * (두 트랜잭션이 거의 동시에 같은 행을 PENDING으로 읽는 경우)까지 막지 못한다는 지적에 대한
 * 실제 검증. `ProblemAllTest.saveAndFlush_withStaleEntity_throwsOptimisticLockingFailure()`
 * (content-service)와 동일한 기법 — 실제 스레드를 띄우는 대신, 같은 행을 각각 detach한 두
 * 인스턴스로 재현한다(둘 다 같은 version을 들고 있다가 하나만 먼저 flush에 성공하는 상황과
 * 동등하다).
 */
class CoachingEventOutboxRepositoryImplTest extends AbstractCoachingRepositoryTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private SpringDataCoachingEventOutboxRepository springDataCoachingEventOutboxRepository;

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private CoachingEventOutboxRepositoryImpl coachingEventOutboxRepository;

    @BeforeEach
    void setUp() {
        coachingEventOutboxRepository = new CoachingEventOutboxRepositoryImpl(springDataCoachingEventOutboxRepository);
    }

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
    @DisplayName("next_attempt_at이 아직 지나지 않은 PENDING 행은 폴링 대상에서 제외된다 "
            + "— head-of-line blocking 방지(PR #123 재검토 2차)")
    void findPollableByStatus_excludesRowsWaitingForBackoff() {
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
                ); // next_attempt_at이 null이라 즉시 폴링 대상

        // when
        List<CoachingEventOutbox> pollable = coachingEventOutboxRepository.findPollableByStatus(OutboxStatus.PENDING, 100);

        // then
        assertThat(pollable)
                .extracting(CoachingEventOutbox::getId)
                .contains(readyNow.getId())
                .doesNotContain(stillBackingOff.getId());
    }

    @Test
    @DisplayName("next_attempt_at이 이미 지난 PENDING 행은 다시 폴링 대상에 포함된다")
    void findPollableByStatus_includesRowsPastBackoffWindow() {
        // given
        CoachingEventOutbox outbox = coachingEventOutboxRepository.save(
                CoachingEventOutbox.create(createCoachingSession(), "CoachingCompleted", payload())
        );
        outbox.incrementAttemptCount();
        outbox.scheduleNextAttempt();
        coachingEventOutboxRepository.save(outbox);

        // 백오프 창을 이미 지난 것처럼 직접 과거로 되돌린다(실제 backoff 시간을 기다리지 않기 위함).
        entityManager.createQuery(
                        "UPDATE CoachingEventOutbox o SET o.nextAttemptAt = :past WHERE o.id = :id")
                .setParameter("past", Instant.now().minusSeconds(60))
                .setParameter("id", outbox.getId())
                .executeUpdate();
        entityManager.clear();

        // when
        List<CoachingEventOutbox> pollable = coachingEventOutboxRepository.findPollableByStatus(OutboxStatus.PENDING, 100);

        // then
        assertThat(pollable).extracting(CoachingEventOutbox::getId).contains(outbox.getId());
    }
}
