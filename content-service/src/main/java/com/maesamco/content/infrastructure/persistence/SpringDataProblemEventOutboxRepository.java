package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataProblemEventOutboxRepository extends JpaRepository<ProblemEventOutbox, UUID> {

    /** 백오프 대기 중인 Outbox를 제외하고 현재 폴링 가능한 Outbox를 조회 */
    @Query("""
            SELECT o FROM ProblemEventOutbox o
            WHERE o.status = :status
              AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
            ORDER BY o.occurredAt ASC, o.id ASC
            """)
    List<ProblemEventOutbox> findPollableByStatus(
            @Param("status") ProblemEventOutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}