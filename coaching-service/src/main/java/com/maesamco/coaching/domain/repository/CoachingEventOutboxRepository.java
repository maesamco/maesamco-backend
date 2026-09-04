package com.maesamco.coaching.domain.repository;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;

/**
 * 이슈 #51은 이 Outbox에 row를 쓰는 것까지만 담당한다. PENDING 상태를 폴링하는 조회
 * 메서드는 이슈 #89(Relay Worker)에서 필요할 때 추가한다.
 */
public interface CoachingEventOutboxRepository {

    CoachingEventOutbox save(CoachingEventOutbox coachingEventOutbox);
}
