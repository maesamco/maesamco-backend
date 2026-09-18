package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
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

interface SpringDataDailyQuizEventOutboxRepository extends JpaRepository<DailyQuizEventOutbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT outbox
            FROM DailyQuizEventOutbox outbox
            WHERE (
                outbox.status = :pendingStatus
                AND (
                    outbox.nextAttemptAt IS NULL
                    OR outbox.nextAttemptAt <= :availableAt
                )
            ) OR (
                outbox.status = :inProgressStatus
                AND outbox.leaseUntil <= :availableAt
            )
            ORDER BY outbox.occurredAt ASC, outbox.id ASC
            """)
    List<DailyQuizEventOutbox> findClaimableForUpdate(
            @Param("pendingStatus") DailyQuizEventOutboxStatus pendingStatus,
            @Param("inProgressStatus") DailyQuizEventOutboxStatus inProgressStatus,
            @Param("availableAt") Instant availableAt,
            Pageable pageable
    );
}
