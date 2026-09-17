package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * DailyQuizCompleted Outbox의 Kafka 발행 결과를 DB 상태에 반영
 *
 * Kafka ACK 대기 중에는 DB 트랜잭션을 유지하지 않고,
 * 발행 결과가 확정된 뒤 이 서비스의 짧은 트랜잭션으로
 * Outbox 상태만 변경합니다.
 */
@Service
@RequiredArgsConstructor
public class DailyQuizEventOutboxStatusService {

    private final DailyQuizEventOutboxRepository outboxRepository;

    /**
     * Kafka 발행 성공 결과를 Outbox에 기록
     */
    @Transactional
    public void recordPublishSuccess(UUID outboxId, Instant publishedAt) {
        DailyQuizEventOutbox outbox = getOutbox(outboxId);

        if (outbox.getStatus() != DailyQuizEventOutboxStatus.PENDING) {
            return;
        }

        outbox.recordPublishSuccess(publishedAt);
    }

    /**
     * Kafka 발행 실패와 다음 재시도 시각을 Outbox에 기록
     */
    @Transactional
    public void recordPublishFailure(
            UUID outboxId,
            String error,
            int maxRetryCount,
            Instant nextAttemptAt
    ) {
        DailyQuizEventOutbox outbox = getOutbox(outboxId);

        if (outbox.getStatus() != DailyQuizEventOutboxStatus.PENDING) {
            return;
        }

        outbox.recordPublishFailure(error, maxRetryCount, nextAttemptAt
        );
    }

    /**
     * 재시도로 복구할 수 없는 Kafka 발행 실패를 Outbox에 기록
     */
    @Transactional
    public void recordUnrecoverablePublishFailure(UUID outboxId, String error) {
        DailyQuizEventOutbox outbox = getOutbox(outboxId);

        if (outbox.getStatus() != DailyQuizEventOutboxStatus.PENDING) {
            return;
        }

        outbox.recordUnrecoverablePublishFailure(error);
    }

    private DailyQuizEventOutbox getOutbox(UUID outboxId) {
        return outboxRepository.findById(outboxId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "Daily Quiz Event Outbox를 찾을 수 없습니다.")
                );
    }
}
