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
     * 지금 발행할 수 있는 PENDING Outbox를 이벤트 발생 시각이 오래된 순서대로 조회
     */
    List<DailyQuizEventOutbox> findPublishablePending(Instant availableAt, int limit);
}
