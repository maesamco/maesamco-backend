package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProblemEventOutboxRepositoryImpl implements ProblemEventOutboxRepository {

    private final SpringDataProblemEventOutboxRepository springDataProblemEventOutboxRepository;

    @Override
    public ProblemEventOutbox save(ProblemEventOutbox problemEventOutbox) {
        return springDataProblemEventOutboxRepository.saveAndFlush(problemEventOutbox);
    }

    @Override
    public Optional<ProblemEventOutbox> findById(UUID outboxId) {
        return springDataProblemEventOutboxRepository.findById(outboxId);
    }

    @Override
    public List<ProblemEventOutbox> findClaimableForUpdate(Instant now, int limit) {
        return springDataProblemEventOutboxRepository.findClaimableForUpdate(
                ProblemEventOutboxStatus.PENDING,
                ProblemEventOutboxStatus.IN_PROGRESS,
                now,
                PageRequest.of(0, limit)
        );
    }
}
