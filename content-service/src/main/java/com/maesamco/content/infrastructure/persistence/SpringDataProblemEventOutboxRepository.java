package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataProblemEventOutboxRepository extends JpaRepository<ProblemEventOutbox, UUID> {

    /** 상태가 일치하는 Outbox를 발생 시각과 ID 오름차순으로 안정적으로 조회 */
    List<ProblemEventOutbox> findAllByStatusOrderByOccurredAtAscIdAsc(
            ProblemEventOutboxStatus status,
            Pageable pageable
    );
}