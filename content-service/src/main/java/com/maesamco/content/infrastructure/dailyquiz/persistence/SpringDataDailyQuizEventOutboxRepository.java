package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataDailyQuizEventOutboxRepository extends JpaRepository<DailyQuizEventOutbox, UUID> {

    @Query("""
            SELECT outbox
            FROM DailyQuizEventOutbox outbox
            WHERE outbox.status = :status
              AND (
                  outbox.nextAttemptAt IS NULL
                  OR outbox.nextAttemptAt <= :availableAt
              )
            ORDER BY outbox.occurredAt ASC, outbox.id ASC
            """)
    List<DailyQuizEventOutbox> findPublishableByStatus(
            @Param("status") DailyQuizEventOutboxStatus status,
            @Param("availableAt") Instant availableAt,
            Pageable pageable
    );
}
