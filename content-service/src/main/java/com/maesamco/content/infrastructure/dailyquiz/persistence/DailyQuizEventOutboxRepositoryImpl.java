package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DailyQuizEventOutboxRepositoryImpl implements DailyQuizEventOutboxRepository {

    private final SpringDataDailyQuizEventOutboxRepository springDataRepository;

    @Override
    public DailyQuizEventOutbox save(DailyQuizEventOutbox outbox) {
        return springDataRepository.save(outbox);
    }

    @Override
    public Optional<DailyQuizEventOutbox> findById(UUID id) {
        return springDataRepository.findById(id);
    }

    @Override
    @Transactional
    public List<DailyQuizEventOutbox> claimPublishable(
            Instant availableAt,
            Instant leaseUntil,
            UUID claimId,
            int limit
    ) {
        List<DailyQuizEventOutbox> claimed = springDataRepository.findClaimableForUpdate(
                DailyQuizEventOutboxStatus.PENDING,
                DailyQuizEventOutboxStatus.IN_PROGRESS,
                availableAt,
                PageRequest.of(0, limit)
        );

        claimed.forEach(outbox ->
                outbox.claimForPublish(claimId, availableAt, leaseUntil)
        );
        springDataRepository.flush();

        return claimed;
    }
}
