package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.application.dailyquiz.service.DailyQuizEventOutboxStatusService;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutboxStatus;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizEventOutboxRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyQuizEventOutboxStatusServiceTest {

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-17T00:00:00Z");

    private static final Instant NEXT_ATTEMPT_AT =
            Instant.parse("2026-09-17T00:01:00Z");

    private static final Instant PUBLISHED_AT =
            Instant.parse("2026-09-17T00:02:00Z");

    private static final Instant LEASE_UNTIL =
            Instant.parse("2026-09-17T00:05:00Z");

    private static final UUID CLAIM_ID = UUID.randomUUID();

    @Mock
    private DailyQuizEventOutboxRepository outboxRepository;

    private DailyQuizEventOutboxStatusService statusService;

    @BeforeEach
    void setUp() {
        statusService =
                new DailyQuizEventOutboxStatusService(
                        outboxRepository
                );
    }

    @Test
    @DisplayName("Kafka 발행 성공 결과를 Outbox에 기록한다")
    void recordPublishSuccess_updatesOutbox() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        statusService.recordPublishSuccess(
                outboxId,
                CLAIM_ID,
                PUBLISHED_AT
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        verify(outboxRepository, never()).save(outbox);
    }

    @Test
    @DisplayName("재시도 가능한 Kafka 발행 실패를 Outbox에 기록한다")
    void recordPublishFailure_updatesRetryState() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        statusService.recordPublishFailure(
                outboxId,
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                NEXT_ATTEMPT_AT
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NEXT_ATTEMPT_AT);
        assertThat(outbox.getLastError()).isEqualTo("KAFKA_PUBLISH_TIMEOUT");
        verify(outboxRepository, never()).save(outbox);
    }

    @Test
    @DisplayName("재시도 한도에 도달한 발행 실패는 FAILED로 변경한다")
    void recordPublishFailure_marksFailedAtRetryLimit() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        statusService.recordPublishFailure(
                outboxId,
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                1,
                NEXT_ATTEMPT_AT
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("발행 결과를 확인할 수 없으면 Outbox를 재시도 대상으로 유지한다")
    void recordPublishOutcomeUnknown_updatesRetryStateWithoutFailure() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        statusService.recordPublishOutcomeUnknown(
                outboxId,
                CLAIM_ID,
                "KAFKA_PUBLISH_OUTCOME_UNKNOWN",
                3,
                NEXT_ATTEMPT_AT
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NEXT_ATTEMPT_AT);
        assertThat(outbox.getLastError())
                .isEqualTo("KAFKA_PUBLISH_OUTCOME_UNKNOWN");
        verify(outboxRepository, never()).save(outbox);
    }

    @Test
    @DisplayName("불확실한 발행 결과가 재시도 한도에 도달하면 UNKNOWN으로 변경한다")
    void recordPublishOutcomeUnknown_marksUnknownAtRetryLimit() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        boolean updated = statusService.recordPublishOutcomeUnknown(
                outboxId,
                CLAIM_ID,
                "KAFKA_PUBLISH_OUTCOME_UNKNOWN",
                1,
                NEXT_ATTEMPT_AT
        );

        assertThat(updated).isTrue();
        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.UNKNOWN);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getClaimId()).isNull();
        assertThat(outbox.getLeaseUntil()).isNull();
    }

    @Test
    @DisplayName("재시도할 수 없는 발행 실패를 즉시 FAILED로 변경한다")
    void recordUnrecoverablePublishFailure_marksFailed() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        statusService.recordUnrecoverablePublishFailure(
                outboxId,
                CLAIM_ID,
                "EVENT_PAYLOAD_TOO_LARGE"
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("EVENT_PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("이미 PUBLISHED인 Outbox의 실패 기록은 멱등하게 무시한다")
    void recordPublishFailure_ignoresPublishedOutbox() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        outbox.recordPublishSuccess(CLAIM_ID, PUBLISHED_AT);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        statusService.recordPublishFailure(
                outboxId,
                CLAIM_ID,
                "KAFKA_PUBLISH_TIMEOUT",
                3,
                NEXT_ATTEMPT_AT
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PUBLISHED);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    @DisplayName("이미 PUBLISHED인 Outbox의 불확실한 발행 결과는 멱등하게 무시한다")
    void recordPublishOutcomeUnknown_ignoresPublishedOutbox() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        outbox.recordPublishSuccess(CLAIM_ID, PUBLISHED_AT);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        statusService.recordPublishOutcomeUnknown(
                outboxId,
                CLAIM_ID,
                "KAFKA_PUBLISH_OUTCOME_UNKNOWN",
                3,
                NEXT_ATTEMPT_AT
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.PUBLISHED);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    @DisplayName("이미 FAILED인 Outbox의 성공 기록은 멱등하게 무시한다")
    void recordPublishSuccess_ignoresFailedOutbox() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        outbox.recordUnrecoverablePublishFailure(
                CLAIM_ID,
                "EVENT_PAYLOAD_TOO_LARGE"
        );
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        statusService.recordPublishSuccess(
                outboxId,
                CLAIM_ID,
                PUBLISHED_AT
        );

        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getLastError()).isEqualTo("EVENT_PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("이전 Worker의 claim ID로 전달된 늦은 결과는 무시한다")
    void recordPublishSuccess_ignoresStaleClaim() {
        UUID outboxId = UUID.randomUUID();
        DailyQuizEventOutbox outbox = createPendingOutbox();
        claim(outbox);
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.of(outbox));

        boolean updated = statusService.recordPublishSuccess(
                outboxId,
                UUID.randomUUID(),
                PUBLISHED_AT
        );

        assertThat(updated).isFalse();
        assertThat(outbox.getStatus())
                .isEqualTo(DailyQuizEventOutboxStatus.IN_PROGRESS);
        assertThat(outbox.getClaimId()).isEqualTo(CLAIM_ID);
    }

    @Test
    @DisplayName("상태를 변경할 Outbox가 없으면 예외가 발생한다")
    void recordPublishSuccess_rejectsMissingOutbox() {
        UUID outboxId = UUID.randomUUID();
        when(outboxRepository.findById(outboxId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> statusService.recordPublishSuccess(
                outboxId,
                CLAIM_ID,
                PUBLISHED_AT
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ENTITY_NOT_FOUND)
                )
                .hasMessage("Daily Quiz Event Outbox를 찾을 수 없습니다.");
    }

    private DailyQuizEventOutbox createPendingOutbox() {
        return DailyQuizEventOutbox.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DAILY_QUIZ_COMPLETED",
                1,
                "{\"eventType\":\"DAILY_QUIZ_COMPLETED\"}",
                OCCURRED_AT
        );
    }

    private void claim(DailyQuizEventOutbox outbox) {
        outbox.claimForPublish(
                CLAIM_ID,
                NEXT_ATTEMPT_AT,
                LEASE_UNTIL
        );
    }
}
