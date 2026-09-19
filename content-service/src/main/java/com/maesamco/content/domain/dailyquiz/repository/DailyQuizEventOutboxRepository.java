package com.maesamco.content.domain.dailyquiz.repository;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Daily Quiz 완료 이벤트 Outbox의 저장과 Relay 조회를 담당
 */
public interface DailyQuizEventOutboxRepository {

    DailyQuizEventOutbox save(DailyQuizEventOutbox outbox);

    Optional<DailyQuizEventOutbox> findById(UUID id);

    /**
     * 지금 발행할 수 있는 Outbox를 오래된 순서대로 잠그고 해당 Worker가 선점합니다.
     *
     * PENDING 또는 lease가 만료된 IN_PROGRESS 행이 대상이며,
     * 다른 트랜잭션이 잠근 행은 기다리지 않고 건너뜁니다.
     */
    List<DailyQuizEventOutbox> claimPublishable(
            Instant availableAt,
            Instant leaseUntil,
            UUID claimId,
            int limit
    );
}
