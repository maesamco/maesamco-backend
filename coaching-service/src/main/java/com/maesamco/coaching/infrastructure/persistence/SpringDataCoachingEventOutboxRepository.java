package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataCoachingEventOutboxRepository extends JpaRepository<CoachingEventOutbox, UUID> {

    // PR #123 재검토 2차(용현님, 2026-09-10) — next_attempt_at이 미래인 행(백오프 대기 중)은
    // 폴링 대상에서 제외해야 head-of-line blocking을 피할 수 있다. derived query로는 이
    // "status 일치 + (next_attempt_at IS NULL OR <= now)" 조합을 자연스럽게 표현할 수 없어
    // @Query로 직접 작성한다.
    @Query("""
            SELECT o FROM CoachingEventOutbox o
            WHERE o.status = :status
              AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
            ORDER BY o.createdAt ASC
            """)
    List<CoachingEventOutbox> findPollableByStatus(
            @Param("status") OutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
