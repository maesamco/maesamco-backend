package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
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

interface SpringDataCoachingEventOutboxRepository extends JpaRepository<CoachingEventOutbox, UUID> {

    // 이슈 #261 — PR #123 재검토 2차가 요구했던 "next_attempt_at이 지나지 않은 행 제외"
    // 조건을, PENDING/IN_PROGRESS(lease 만료) 두 상태를 함께 고르는 선점 쿼리로 확장했다.
    // FOR UPDATE SKIP LOCKED로 다른 트랜잭션이 이미 잠근 행은 기다리지 않고 건너뛴다 —
    // 두 Relay 인스턴스가 같은 행을 동시에 선점하지 못하게 하는 핵심 장치다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT o FROM CoachingEventOutbox o
            WHERE (
                o.status = :pendingStatus
                AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :claimedAt)
            ) OR (
                o.status = :inProgressStatus
                AND o.leaseUntil <= :claimedAt
            )
            ORDER BY o.createdAt ASC
            """)
    List<CoachingEventOutbox> findClaimableForUpdate(
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("inProgressStatus") OutboxStatus inProgressStatus,
            @Param("claimedAt") Instant claimedAt,
            Pageable pageable
    );
}
