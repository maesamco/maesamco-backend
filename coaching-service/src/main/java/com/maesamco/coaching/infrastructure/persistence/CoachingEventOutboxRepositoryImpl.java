package com.maesamco.coaching.infrastructure.persistence;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CoachingEventOutboxRepositoryImpl implements CoachingEventOutboxRepository {

    private final SpringDataCoachingEventOutboxRepository springDataCoachingEventOutboxRepository;

    /**
     * saveAndFlush로 즉시 flush한다 — ExplanationRepositoryImpl.save()와 동일한 패턴(PR #8).
     * {@code @Version} 낙관적 락 위반(ObjectOptimisticLockingFailureException)을 트랜잭션
     * 커밋 시점이 아니라 이 메서드 호출 지점에서 바로 던지게 해서, 호출자
     * (CoachingEventOutboxPersistenceService)가 자기 메서드 본문의 try-catch로 잡을 수 있게
     * 한다(PR #123 재검토 2차).
     */
    @Override
    public CoachingEventOutbox save(CoachingEventOutbox coachingEventOutbox) {
        return springDataCoachingEventOutboxRepository.saveAndFlush(coachingEventOutbox);
    }

    @Override
    public Optional<CoachingEventOutbox> findById(UUID id) {
        return springDataCoachingEventOutboxRepository.findById(id);
    }

    @Override
    public List<CoachingEventOutbox> findPollableByStatus(OutboxStatus status, int limit) {
        return springDataCoachingEventOutboxRepository.findPollableByStatus(
                status, Instant.now(), PageRequest.of(0, limit));
    }
}
