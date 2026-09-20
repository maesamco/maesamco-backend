package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemEventOutboxRepository {

    ProblemEventOutbox save(ProblemEventOutbox problemEventOutbox);

    Optional<ProblemEventOutbox> findById(UUID outboxId);

    List<ProblemEventOutbox> findAllByStatusOrderByOccurredAtAscIdAsc(
            ProblemEventOutboxStatus status,
            int limit
    );
}