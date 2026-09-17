package com.maesamco.content.domain.dailyquiz.repository;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;

/**
 * Daily Quiz 완료 이벤트 Outbox의 저장을 담당
 */
public interface DailyQuizEventOutboxRepository {

    DailyQuizEventOutbox save(DailyQuizEventOutbox outbox);
}
