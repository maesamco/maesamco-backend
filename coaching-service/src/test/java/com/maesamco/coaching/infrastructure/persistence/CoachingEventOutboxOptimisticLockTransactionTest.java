package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.application.persistence_service.CoachingEventOutboxPersistenceService;
import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.CoachingSession;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

/**
 * 이슈 #288 — {@link CoachingEventOutboxPersistenceService}의 결과 기록 메서드가 낙관적 락 충돌을
 * 삼키지 않고 그대로 전파하는지, 그리고 트랜잭션이 {@link UnexpectedRollbackException}으로
 * 어긋나지 않는지를 실제 PostgreSQL에서 검증한다.
 *
 * <p>처음에는 각 메서드가 {@code ObjectOptimisticLockingFailureException}을 안에서 catch했는데,
 * 안쪽 {@code save()}(Spring Data 트랜잭션 advice)에서 예외가 나가면 바깥 트랜잭션이 이미
 * rollback-only로 표시돼 catch로 정상 반환해도 커밋 시점에 {@code UnexpectedRollbackException}이
 * 새로 발생했다(이 테스트로 재현·확인). 지금은 catch 없이 예외를 그대로 내보내고 Facade가 처리한다.</p>
 *
 * <p>{@code findById()}와 {@code save()} 사이에 다른 트랜잭션이 끼어드는 상황은, 저장소를 감싸서
 * {@code findById()} 직후 별도 트랜잭션(REQUIRES_NEW)으로 같은 행의 {@code version}을 올려 재현한다.
 * 이 테스트가 보장하는 범위는 "{@code save()} 시점에 낙관적 락 충돌이 결정적으로 발생했을 때 서비스가
 * 예외를 삼키지 않고 전파하며 {@link UnexpectedRollbackException}이 생기지 않는다"까지다.
 * 실제 lease 만료 후 다른 Relay가 재선점하는 경합에서는 {@code version}뿐 아니라 {@code claim_id},
 * {@code lease_until}도 함께 바뀌는데, 그 재선점·fencing 상태 전이 전체를 재현하지는 않는다
 * (그 부분은 {@code CoachingEventOutboxRepositoryImplTest}의 선점 테스트와 서비스 단위 테스트가 다룬다).</p>
 */
@Import({CoachingEventOutboxRepositoryImpl.class, CoachingEventOutboxPersistenceService.class})
class CoachingEventOutboxOptimisticLockTransactionTest extends AbstractCoachingRepositoryTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String OUTBOX_TABLE = "coaching_schema.p_coaching_event_outboxes";

    @Autowired
    private CoachingEventOutboxPersistenceService persistenceService;

    @MockitoSpyBean
    private CoachingEventOutboxRepository coachingEventOutboxRepository;

    @Autowired
    private SpringDataCoachingSessionRepository springDataCoachingSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 저장소의 findById() 직후 다른 트랜잭션이 같은 행을 갱신(version 증가)하도록 만든다. */
    private void interleaveVersionBumpAfterFindById(UUID outboxId) {
        DefaultTransactionDefinition requiresNew = new DefaultTransactionDefinition();
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            new TransactionTemplate(transactionManager, requiresNew).executeWithoutResult(status ->
                    jdbcTemplate.update(
                            "UPDATE " + OUTBOX_TABLE + " SET version = version + 1 WHERE id = ?",
                            outboxId
                    ));
            return result;
        }).when(coachingEventOutboxRepository).findById(outboxId);
    }

    private UUID givenClaimedOutbox(UUID claimId) {
        CoachingSession session = CoachingSession.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
        UUID sessionId = springDataCoachingSessionRepository.save(session).getId();
        CoachingEventOutbox saved = coachingEventOutboxRepository.save(
                CoachingEventOutbox.create(
                        sessionId,
                        "CoachingCompleted",
                        JSON_MAPPER.createObjectNode().put("coachingId", UUID.randomUUID().toString())
                )
        );
        Instant now = Instant.now();
        coachingEventOutboxRepository.claimPublishable(now, now.plus(Duration.ofMinutes(5)), claimId, 1);
        return saved.getId();
    }

    private void assertPropagatesOptimisticLockingFailure(BiConsumer<UUID, UUID> resultRecorder) {
        jdbcTemplate.update("DELETE FROM " + OUTBOX_TABLE);
        UUID claimId = UUID.randomUUID();
        UUID outboxId = givenClaimedOutbox(claimId);
        long versionAfterClaim = jdbcTemplate.queryForObject(
                "SELECT version FROM " + OUTBOX_TABLE + " WHERE id = ?", Long.class, outboxId);
        interleaveVersionBumpAfterFindById(outboxId);

        try {
            assertThatThrownBy(() -> resultRecorder.accept(outboxId, claimId))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class)
                    .isNotInstanceOf(UnexpectedRollbackException.class);

            // 충돌한 Worker는 아무것도 덮어쓰지 않는다 — 끼어든 트랜잭션이 올린 version만 반영돼 있다.
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT status, attempt_count, version FROM " + OUTBOX_TABLE + " WHERE id = ?", outboxId);
            assertThat(row.get("status")).isEqualTo("IN_PROGRESS");
            assertThat(((Number) row.get("attempt_count")).intValue()).isZero();
            assertThat(((Number) row.get("version")).longValue()).isEqualTo(versionAfterClaim + 1);
        } finally {
            jdbcTemplate.update("DELETE FROM " + OUTBOX_TABLE);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("markPublished는 낙관적 락 충돌을 삼키지 않고 전파하며 UnexpectedRollbackException을 만들지 않는다")
    void markPublished_optimisticLockConflict_propagates() {
        assertPropagatesOptimisticLockingFailure(persistenceService::markPublished);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("recordFailedAttempt는 낙관적 락 충돌을 삼키지 않고 전파한다")
    void recordFailedAttempt_optimisticLockConflict_propagates() {
        assertPropagatesOptimisticLockingFailure(persistenceService::recordFailedAttempt);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("recordPostPublishFailure는 낙관적 락 충돌을 삼키지 않고 전파한다")
    void recordPostPublishFailure_optimisticLockConflict_propagates() {
        assertPropagatesOptimisticLockingFailure(persistenceService::recordPostPublishFailure);
    }
}
