package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataProblemEventOutboxRepository extends JpaRepository<ProblemEventOutbox, UUID> {

    /**
     * 선점 가능한 Outbox를 SELECT ... FOR UPDATE SKIP LOCKED 로 조회합니다.
     *
     * <p>lock.timeout = -2 는 Hibernate에서 SKIP LOCKED를 의미합니다.
     * 다른 Relay 인스턴스가 선점 트랜잭션 중인 행은 대기 없이 건너뛰므로
     * 여러 인스턴스가 같은 행을 동시에 선점하지 않습니다.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT o FROM ProblemEventOutbox o
            WHERE (
                o.status = :pendingStatus
                AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
            ) OR (
                o.status = :inProgressStatus
                AND o.leaseUntil <= :now
            )
            ORDER BY o.occurredAt ASC, o.id ASC
            """)
    List<ProblemEventOutbox> findClaimableForUpdate(
            @Param("pendingStatus") ProblemEventOutboxStatus pendingStatus,
            @Param("inProgressStatus") ProblemEventOutboxStatus inProgressStatus,
            @Param("now") Instant now,
            Pageable pageable
    );
}
