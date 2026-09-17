package com.maesamco.content.application.dailyquiz.service;

import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPublishOutcomeUnknownException;
import com.maesamco.content.application.dailyquiz.port.DailyQuizCompletedEventPublisherPort;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 발행 가능한 DailyQuizCompleted Outbox를 조회해 Kafka로 전달
 *
 * Kafka ACK를 기다리는 동안 DB 트랜잭션을 유지하지 않습니다.
 * 발행 결과가 정해진 뒤 DailyQuizEventOutboxStatusService
 * 짧은 트랜잭션으로 Outbox 상태만 변경
 *
 * Kafka 발행 성공 후 PUBLISHED 저장 전에 장애가 발생하면 동일 이벤트가
 * 다시 발행될 수 있으므로 전달 방식은 At-least-once
 */
@Slf4j
@Service
public class DailyQuizEventOutboxRelayService {

    private static final String PAYLOAD_TOO_LARGE_ERROR = "EVENT_PAYLOAD_TOO_LARGE";

    private static final String PUBLISH_OUTCOME_UNKNOWN_ERROR = "KAFKA_PUBLISH_OUTCOME_UNKNOWN";

    private static final String PUBLISH_STATUS_UPDATE_FAILED_ERROR = "KAFKA_PUBLISHED_STATUS_UPDATE_FAILED";

    private final DailyQuizEventOutboxRepository outboxRepository;
    private final DailyQuizCompletedEventPublisherPort eventPublisherPort;
    private final DailyQuizEventOutboxStatusService statusService;
    private final Clock dailyQuizClock;
    private final int batchSize;
    private final int maxPayloadBytes;
    private final int maxRetryCount;
    private final long backoffBaseMillis;
    private final long backoffMaxMillis;

    public DailyQuizEventOutboxRelayService(
            DailyQuizEventOutboxRepository outboxRepository,
            DailyQuizCompletedEventPublisherPort eventPublisherPort,
            DailyQuizEventOutboxStatusService statusService,
            Clock dailyQuizClock,
            @Value("${outbox.daily-quiz-completed.relay.batch-size:50}")
            int batchSize,

            @Value("${outbox.daily-quiz-completed.relay.max-payload-bytes:900000}")
            int maxPayloadBytes,

            @Value("${outbox.daily-quiz-completed.relay.max-retry-count:10}")
            int maxRetryCount,

            @Value("${outbox.daily-quiz-completed.relay.backoff-base-ms:1000}")
            long backoffBaseMillis,

            @Value("${outbox.daily-quiz-completed.relay.backoff-max-ms:60000}")
            long backoffMaxMillis
    ) {
        this.outboxRepository = outboxRepository;
        this.eventPublisherPort = eventPublisherPort;
        this.statusService = statusService;
        this.dailyQuizClock = dailyQuizClock;
        this.batchSize = batchSize;
        this.maxPayloadBytes = maxPayloadBytes;
        this.maxRetryCount = maxRetryCount;
        this.backoffBaseMillis = backoffBaseMillis;
        this.backoffMaxMillis = backoffMaxMillis;
    }

    /**
     * 현재 발행할 수 있는 PENDING Outbox를 오래된 순서대로 처리
     */
    public void relayPendingOutboxes() {
        List<DailyQuizEventOutbox> outboxes =
                outboxRepository.findPublishablePending(
                        dailyQuizClock.instant(),
                        batchSize
                );

        for (DailyQuizEventOutbox outbox : outboxes) {
            boolean continueRelay = relayOne(outbox);

            if (!continueRelay) {
                return;
            }
        }
    }

    /**
     * Outbox 한 건을 처리
     *
     * 남은 배치를 계속 처리할 수 있으면 true 현재 스레드가 interrupt되었으면 false
     */
    private boolean relayOne(DailyQuizEventOutbox outbox) {
        if (isPayloadTooLarge(outbox.getPayload())) {
            recordUnrecoverableFailureSafely(
                    outbox,
                    PAYLOAD_TOO_LARGE_ERROR
            );
            return true;
        }

        try {
            eventPublisherPort.publish(
                    outbox.getAggregateId(),
                    outbox.getPayload()
            );
        } catch (DailyQuizCompletedEventPublishOutcomeUnknownException exception) {
            recordPublishOutcomeUnknownSafely(
                    outbox,
                    PUBLISH_OUTCOME_UNKNOWN_ERROR
            );

            return !Thread.currentThread().isInterrupted();
        } catch (RuntimeException exception) {
            recordPublishFailureSafely(
                    outbox,
                    safePublishError(exception)
            );
            return true;
        }

        recordPublishSuccessSafely(outbox);
        return true;
    }

    private boolean isPayloadTooLarge(String payload) {
        return payload.getBytes(StandardCharsets.UTF_8).length
                > maxPayloadBytes;
    }

    /**
     * ACK가 확인된 이벤트를 PUBLISHED로 변경
     *
     * 상태 저장에 실패해도 Kafka 발행 실패로 기록하지 않습니다.
     * 가능한 경우 PENDING 상태에 다음 시도 시각을 기록하고,
     * 이 기록도 실패하면 다음 polling에서 다시 조회되도록 둡니다.
     */
    private void recordPublishSuccessSafely(DailyQuizEventOutbox outbox) {
        try {
            statusService.recordPublishSuccess(
                    outbox.getId(),
                    dailyQuizClock.instant()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "DailyQuizCompleted Kafka 발행 후 Outbox 상태 갱신 실패. "
                            + "outboxId={}, eventId={}, quizAttemptId={}, errorType={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    exception.getClass().getSimpleName(),
                    exception
            );

            recordPublishOutcomeUnknownSafely(
                    outbox,
                    PUBLISH_STATUS_UPDATE_FAILED_ERROR
            );
        }
    }

    private void recordPublishFailureSafely(
            DailyQuizEventOutbox outbox,
            String safeError
    ) {
        try {
            statusService.recordPublishFailure(
                    outbox.getId(),
                    safeError,
                    maxRetryCount,
                    nextAttemptAt(outbox.getRetryCount())
            );
        } catch (RuntimeException exception) {
            log.error(
                    "DailyQuizCompleted Outbox 발행 실패 상태 저장 실패. "
                            + "outboxId={}, eventId={}, quizAttemptId={}, errorType={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    exception.getClass().getSimpleName(),
                    exception
            );
        }
    }

    private void recordPublishOutcomeUnknownSafely(
            DailyQuizEventOutbox outbox,
            String safeError
    ) {
        try {
            statusService.recordPublishOutcomeUnknown(
                    outbox.getId(),
                    safeError,
                    nextAttemptAt(outbox.getRetryCount())
            );
        } catch (RuntimeException exception) {
            log.error(
                    "DailyQuizCompleted Outbox 발행 결과 불확실 상태 저장 실패. "
                            + "outboxId={}, eventId={}, quizAttemptId={}, errorType={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    exception.getClass().getSimpleName(),
                    exception
            );
        }
    }

    private void recordUnrecoverableFailureSafely(
            DailyQuizEventOutbox outbox,
            String safeError
    ) {
        try {
            statusService.recordUnrecoverablePublishFailure(
                    outbox.getId(),
                    safeError
            );
        } catch (RuntimeException exception) {
            log.error(
                    "DailyQuizCompleted Outbox 복구 불가능 상태 저장 실패. "
                            + "outboxId={}, eventId={}, quizAttemptId={}, errorType={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    exception.getClass().getSimpleName(),
                    exception
            );
        }
    }

    private Instant nextAttemptAt(int retryCount) {
        return dailyQuizClock.instant().plusMillis(
                calculateBackoffMillis(retryCount)
        );
    }

    /**
     * 첫 실패에는 기본 지연을 적용하고 이후 실패마다 두 배로 늘림
     * 설정된 최대 지연을 넘지 않도록 제한
     */
    private long calculateBackoffMillis(int retryCount) {
        long backoffMillis = Math.min(
                backoffBaseMillis,
                backoffMaxMillis
        );

        for (int count = 0; count < retryCount; count++) {
            if (backoffMillis >= backoffMaxMillis
                    || backoffMillis > backoffMaxMillis / 2) {
                return backoffMaxMillis;
            }

            backoffMillis *= 2;
        }

        return backoffMillis;
    }

    /**
     * Kafka 예외 메시지에는 payload가 포함될 수 있으므로 DB에는 예외 타입만 저장
     */
    private String safePublishError(RuntimeException exception) {
        Throwable cause = exception.getCause();
        Throwable safeCause = cause == null ? exception : cause;

        return "KAFKA_PUBLISH_FAILED:"
                + safeCause.getClass().getSimpleName();
    }
}
