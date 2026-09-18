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
import java.util.UUID;

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

    private static final long MIN_LEASE_SAFETY_MARGIN_MILLIS = 1_000L;

    private static final String PAYLOAD_TOO_LARGE_ERROR = "EVENT_PAYLOAD_TOO_LARGE";

    private static final String PUBLISH_OUTCOME_UNKNOWN_ERROR = "KAFKA_PUBLISH_OUTCOME_UNKNOWN";

    private static final String PUBLISH_STATUS_UPDATE_FAILED_ERROR = "KAFKA_PUBLISHED_STATUS_UPDATE_FAILED";

    private final DailyQuizEventOutboxRepository outboxRepository;
    private final DailyQuizCompletedEventPublisherPort eventPublisherPort;
    private final DailyQuizEventOutboxStatusService statusService;
    private final Clock dailyQuizClock;
    private final int batchSize;
    private final long leaseDurationMillis;
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

            @Value("${outbox.daily-quiz-completed.relay.lease-duration-ms:300000}")
            long leaseDurationMillis,

            @Value("${outbox.daily-quiz-completed.relay.publish-timeout-ms:5000}")
            long publishTimeoutMillis,

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
        validateRelaySettings(
                batchSize,
                leaseDurationMillis,
                publishTimeoutMillis
        );
        this.batchSize = batchSize;
        this.leaseDurationMillis = leaseDurationMillis;
        this.maxPayloadBytes = maxPayloadBytes;
        this.maxRetryCount = maxRetryCount;
        this.backoffBaseMillis = backoffBaseMillis;
        this.backoffMaxMillis = backoffMaxMillis;
    }

    /**
     * Outbox를 발행 직전에 한 건씩 선점하여 최대 batchSize만큼 처리
     */
    public void relayPendingOutboxes() {
        for (int processedCount = 0;
             processedCount < batchSize;
             processedCount++) {
            Instant claimedAt = dailyQuizClock.instant();
            List<DailyQuizEventOutbox> claimed =
                    outboxRepository.claimPublishable(
                            claimedAt,
                            claimedAt.plusMillis(leaseDurationMillis),
                            UUID.randomUUID(),
                            1
                    );

            if (claimed.isEmpty()) {
                return;
            }

            if (!relayOne(claimed.get(0))) {
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
        int payloadBytes = payloadBytes(outbox.getPayload());

        if (payloadBytes > maxPayloadBytes) {
            recordUnrecoverableFailureSafely(
                    outbox,
                    PAYLOAD_TOO_LARGE_ERROR
            );

            log.error(
                    "DailyQuizCompleted Outbox payload 크기 초과. "
                            + "outboxId={}, eventId={}, quizAttemptId={}, "
                            + "payloadBytes={}, maxPayloadBytes={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    payloadBytes,
                    maxPayloadBytes
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

            log.error(
                    "DailyQuizCompleted Outbox 발행 결과 불확실. "
                            + "outboxId={}, eventId={}, quizAttemptId={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    exception
            );

            if (Thread.currentThread().isInterrupted()) {
                log.warn(
                        "DailyQuizCompleted Outbox Relay 인터럽트 감지 - "
                                + "남은 배치 처리를 중단합니다. outboxId={}",
                        outbox.getId()
                );
                return false;
            }

            return true;
        } catch (RuntimeException exception) {
            String safeError = safePublishError(exception);

            recordPublishFailureSafely(
                    outbox,
                    safeError
            );

            log.error(
                    "DailyQuizCompleted Outbox 발행 실패. "
                            + "outboxId={}, eventId={}, quizAttemptId={}, errorType={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId(),
                    safeError,
                    exception
            );
            return true;
        }

        if (recordPublishSuccessSafely(outbox)) {
            log.info(
                    "DailyQuizCompleted Outbox 발행 성공. "
                            + "outboxId={}, eventId={}, quizAttemptId={}",
                    outbox.getId(),
                    outbox.getEventId(),
                    outbox.getAggregateId()
            );
        }
        return true;
    }

    private int payloadBytes(String payload) {
        return payload.getBytes(StandardCharsets.UTF_8).length;
    }

    private void validateRelaySettings(
            int batchSize,
            long leaseDurationMillis,
            long publishTimeoutMillis
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Outbox Relay 배치 크기는 1 이상이어야 합니다.");
        }
        if (publishTimeoutMillis < 1) {
            throw new IllegalArgumentException("Kafka ACK 대기 시간은 1ms 이상이어야 합니다.");
        }
        if (leaseDurationMillis <= publishTimeoutMillis
                || leaseDurationMillis - publishTimeoutMillis
                < MIN_LEASE_SAFETY_MARGIN_MILLIS) {
            throw new IllegalArgumentException(
                    "Outbox 선점 시간은 Kafka ACK 대기 시간보다 "
                            + "최소 1000ms 길어야 합니다."
            );
        }
    }

    /**
     * ACK가 확인된 이벤트를 PUBLISHED로 변경
     *
     * 상태 저장에 실패해도 Kafka 발행 실패로 기록하지 않습니다.
     * 가능한 경우 PENDING 상태에 다음 시도 시각을 기록하고,
     * 이 기록도 실패하면 다음 polling에서 다시 조회되도록 둡니다.
     */
    private boolean recordPublishSuccessSafely(DailyQuizEventOutbox outbox) {
        try {
            return statusService.recordPublishSuccess(
                    outbox.getId(),
                    outbox.getClaimId(),
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
            return false;
        }
    }

    private void recordPublishFailureSafely(
            DailyQuizEventOutbox outbox,
            String safeError
    ) {
        try {
            statusService.recordPublishFailure(
                    outbox.getId(),
                    outbox.getClaimId(),
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
                    outbox.getClaimId(),
                    safeError,
                    maxRetryCount,
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
                    outbox.getClaimId(),
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
