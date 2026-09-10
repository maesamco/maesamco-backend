package com.maesamco.content.problem.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.problem.domain.entity.ProblemEventOutbox;
import com.maesamco.content.problem.domain.repository.ProblemEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * ProblemPublished Outbox의 발행 결과 상태를 갱신합니다.
 *
 * <p>Kafka 전송 자체는 이 서비스의 책임이 아니며,
 * Kafka 발행이 완료된 이후 짧은 DB 트랜잭션으로
 * Outbox 상태만 변경합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class ProblemEventOutboxStatusService {

    private final ProblemEventOutboxRepository
            problemEventOutboxRepository;

    /**
     * Kafka 발행 성공 결과를 Outbox에 반영합니다.
     *
     * @param outboxId    Outbox ID
     * @param publishedAt Kafka 발행 완료 시각
     */
    @Transactional
    public void markPublished(
            UUID outboxId,
            Instant publishedAt
    ) {
        ProblemEventOutbox outbox =
                getOutbox(outboxId);

        outbox.markPublished(
                publishedAt
        );
    }

    /**
     * Kafka 발행 실패 결과를 Outbox에 반영합니다.
     *
     * <p>상태는 PENDING으로 유지되며,
     * retryCount와 안전하게 정제된 실패 사유를 기록합니다.</p>
     *
     * @param outboxId Outbox ID
     * @param error    payload를 포함하지 않는 실패 사유
     */
    @Transactional
    public void recordFailure(
            UUID outboxId,
            String error
    ) {
        ProblemEventOutbox outbox =
                getOutbox(outboxId);

        outbox.recordFailure(
                error
        );
    }

    /**
     * 재시도로 복구할 수 없는 발행 실패를 Outbox에 반영합니다.
     *
     * @param outboxId Outbox ID
     * @param error    payload를 포함하지 않는 실패 사유
     */
    @Transactional
    public void markFailed(
            UUID outboxId,
            String error
    ) {
        ProblemEventOutbox outbox =
                getOutbox(outboxId);

        outbox.markFailed(
                error
        );
    }

    private ProblemEventOutbox getOutbox(
            UUID outboxId
    ) {
        return problemEventOutboxRepository
                .findById(outboxId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.ENTITY_NOT_FOUND,
                                "Problem event Outbox를 찾을 수 없습니다."
                        )
                );
    }
}
