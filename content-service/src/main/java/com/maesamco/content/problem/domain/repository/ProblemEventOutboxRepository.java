package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.enums.ProblemEventOutboxStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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

    /**
     * 특정 상태의 Outbox를 발생 시각이 오래된 순서대로
     * 지정된 배치 크기만큼 조회합니다.
     *
     * <p>Relay는 PENDING 상태의 이벤트를 이 메서드로 조회하여
     * 오래된 이벤트부터 Kafka 발행을 시도합니다.</p>
     *
     * @param status 조회할 Outbox 상태
     * @param pageable 조회할 페이지와 배치 크기
     * @return 발생 시각 오름차순 Outbox 목록
     */
    @NonNull
    List<ProblemEventOutbox> findAllByStatusOrderByOccurredAtAsc(
            @NonNull ProblemEventOutboxStatus status,
            @NonNull Pageable pageable
    );
}
