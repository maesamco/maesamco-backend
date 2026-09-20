package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProblemEventOutboxRepositoryImpl implements ProblemEventOutboxRepository {

    private final SpringDataProblemEventOutboxRepository springDataProblemEventOutboxRepository;

    @Override
    public ProblemEventOutbox save(ProblemEventOutbox problemEventOutbox) {
        return springDataProblemEventOutboxRepository.save(problemEventOutbox);
    }

    @Override
    public Optional<ProblemEventOutbox> findById(UUID outboxId) {
        return springDataProblemEventOutboxRepository.findById(outboxId);
    }

    @Override
    public List<ProblemEventOutbox> findAllByStatusOrderByOccurredAtAscIdAsc(ProblemEventOutboxStatus status, int limit) {
        return springDataProblemEventOutboxRepository.findAllByStatusOrderByOccurredAtAscIdAsc(
                status,
                PageRequest.of(0, limit)
        );
    }
}