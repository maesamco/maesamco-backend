package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemEventOutboxRepository {

    ProblemEventOutbox save(ProblemEventOutbox problemEventOutbox);

    Optional<ProblemEventOutbox> findById(UUID outboxId);

    /**
     * 선점 가능한 Outbox를 행 잠금과 함께 조회합니다.
     *
     * <p>재시도 시각이 도래한 PENDING 행과 lease가 만료된 IN_PROGRESS 행이 대상입니다.
     * 다른 트랜잭션이 이미 잠근 행은 기다리지 않고 건너뜁니다(FOR UPDATE SKIP LOCKED).
     * 반드시 트랜잭션 안에서 호출해야 합니다.</p>
     *
     * @param now 선점 기준 시각
     * @param limit 최대 조회 건수
     * @return 잠금이 걸린 선점 후보 Outbox 목록
     */
    List<ProblemEventOutbox> findClaimableForUpdate(Instant now, int limit);
}
