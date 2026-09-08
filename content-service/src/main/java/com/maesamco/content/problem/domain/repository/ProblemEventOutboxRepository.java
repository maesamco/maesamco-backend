package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Problem 이벤트 Outbox의 저장과 조회를 담당하는 JPA Repository입니다.
 */
public interface ProblemEventOutboxRepository
        extends JpaRepository<ProblemEventOutbox, UUID> {

    @NonNull
    Optional<ProblemEventOutbox> findById(
            @NonNull UUID id
    );
}
