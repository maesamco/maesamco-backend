package com.maesamco.judge.domain.repository;

import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
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

public interface SubmissionEventOutboxRepository extends JpaRepository<SubmissionEventOutbox, UUID> {

    /**
     * 지금 선점할 수 있는 Outbox를 SELECT ... FOR UPDATE SKIP LOCKED 로 조회한다(#272).
     * PENDING이거나 lease가 만료된 IN_PROGRESS 행이 대상이며, 다른 트랜잭션이 잠근 행은 기다리지 않고
     * 건너뛴다(lock.timeout = -2 는 Hibernate에서 SKIP LOCKED). 호출 트랜잭션 안에서 바로 선점 상태로
     * 바꾸고 커밋해야 한다 — 잠금은 커밋과 함께 풀리므로 Kafka 응답을 기다리는 동안 DB 잠금을 쥐지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT o FROM SubmissionEventOutbox o
            WHERE o.status = :pendingStatus
               OR (o.status = :inProgressStatus AND o.leaseUntil <= :now)
            ORDER BY o.createdAt ASC, o.id ASC
            """)
    List<SubmissionEventOutbox> findClaimableForUpdate(
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("inProgressStatus") OutboxStatus inProgressStatus,
            @Param("now") Instant now,
            Pageable pageable
    );
}
