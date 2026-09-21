package com.maesamco.content.application.persistence_service;

import com.maesamco.content.domain.entity.problem.ProblemEventOutbox;
import com.maesamco.content.domain.entity.problem.ProblemEventOutboxStatus;
import com.maesamco.content.domain.repository.problem.ProblemEventOutboxRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemEventOutboxPersistenceServiceTest {

    @Mock
    private ProblemEventOutboxRepository problemEventOutboxRepository;

    private ProblemEventOutboxPersistenceService problemEventOutboxPersistenceService;

    private UUID outboxId;
    private UUID eventId;
    private UUID problemId;

    @BeforeEach
    void setUp() {
        problemEventOutboxPersistenceService = new ProblemEventOutboxPersistenceService(problemEventOutboxRepository);

        outboxId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        problemId = UUID.randomUUID();
    }

    @Test
    @DisplayName("PENDING Outbox의 Kafka 발행 성공을 PUBLISHED 상태로 기록한다")
    void markPublished_pending_marksPublished() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        Instant before = Instant.now();

        // when
        problemEventOutboxPersistenceService.markPublished(outboxId);

        Instant after = Instant.now();

        // then
        assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
        assertThat(outbox.getPublishedAt()).isBetween(before, after);
        assertThat(outbox.getLastError()).isNull();

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository).save(outbox);
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("PENDING이 아닌 Outbox에는 발행 성공 상태를 다시 반영하지 않는다")
    void markPublished_notPending_doesNothing() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();
        outbox.markPublished(Instant.parse("2026-09-21T00:00:00Z"));

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        // when
        problemEventOutboxPersistenceService.markPublished(outboxId);

        // then
        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository, never()).save(any());
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("Kafka 발행 실패를 기록하면 retryCount를 증가시키고 PENDING 상태를 유지한다")
    void recordFailedAttempt_belowMaxRetry_increasesRetryCount() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();
        String error = "Kafka publish failed";

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        // when
        problemEventOutboxPersistenceService.recordFailedAttempt(outboxId, error);

        // then
        assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo(error);
        assertThat(outbox.getPublishedAt()).isNull();

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository).save(outbox);
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("Kafka 발행 실패가 최대 재시도 횟수에 도달하면 FAILED 상태로 변경한다")
    void recordFailedAttempt_reachesMaxRetry_marksFailed() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();

        outbox.recordFailure("failure-1", 5);
        outbox.recordFailure("failure-2", 5);
        outbox.recordFailure("failure-3", 5);
        outbox.recordFailure("failure-4", 5);

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        // when
        problemEventOutboxPersistenceService.recordFailedAttempt(outboxId, "failure-5");

        // then
        assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(5);
        assertThat(outbox.getLastError()).isEqualTo("failure-5");
        assertThat(outbox.getPublishedAt()).isNull();

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository).save(outbox);
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("PENDING이 아닌 Outbox에는 Kafka 발행 실패를 다시 기록하지 않는다")
    void recordFailedAttempt_notPending_doesNothing() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();
        outbox.markFailed("permanent failure");

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        // when
        problemEventOutboxPersistenceService.recordFailedAttempt(outboxId, "another failure");

        // then
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("permanent failure");

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository, never()).save(any());
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("Kafka 발행 결과를 확정할 수 없는 실패는 retryCount를 증가시키지 않고 PENDING 상태를 유지한다")
    void recordPostPublishFailure_pending_keepsPendingWithoutRetryIncrease() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();
        String error = "Kafka publish outcome unknown";

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        // when
        problemEventOutboxPersistenceService.recordPostPublishFailure(outboxId, error);

        // then
        assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getLastError()).isEqualTo(error);
        assertThat(outbox.getPublishedAt()).isNull();

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository).save(outbox);
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("PENDING이 아닌 Outbox에는 발행 결과 불명확 실패를 다시 기록하지 않는다")
    void recordPostPublishFailure_notPending_doesNothing() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();
        outbox.markPublished(Instant.parse("2026-09-21T00:00:00Z"));

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        // when
        problemEventOutboxPersistenceService.recordPostPublishFailure(outboxId, "unknown");

        // then
        assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.PUBLISHED);
        assertThat(outbox.getLastError()).isNull();

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository, never()).save(any());
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("복구할 수 없는 Kafka 발행 실패는 즉시 FAILED 상태로 변경한다")
    void markFailed_pending_marksFailed() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();
        String error = "non-retryable failure";

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        // when
        problemEventOutboxPersistenceService.markFailed(outboxId, error);

        // then
        assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo(error);
        assertThat(outbox.getPublishedAt()).isNull();

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository).save(outbox);
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("PENDING이 아닌 Outbox에는 복구 불가능 실패를 다시 기록하지 않는다")
    void markFailed_notPending_doesNothing() {
        // given
        ProblemEventOutbox outbox = createPendingOutbox();
        outbox.markFailed("first failure");

        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        // when
        problemEventOutboxPersistenceService.markFailed(outboxId, "second failure");

        // then
        assertThat(outbox.getStatus()).isEqualTo(ProblemEventOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("first failure");

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository, never()).save(any());
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    @Test
    @DisplayName("존재하지 않는 Outbox를 상태 변경하려고 하면 예외가 발생한다")
    void markPublished_outboxNotFound_throwsException() {
        // given
        when(problemEventOutboxRepository.findById(outboxId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> problemEventOutboxPersistenceService.markPublished(outboxId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Problem Event Outbox를 찾을 수 없습니다. outboxId=" + outboxId);

        verify(problemEventOutboxRepository).findById(outboxId);
        verify(problemEventOutboxRepository, never()).save(any());
        verifyNoMoreInteractions(problemEventOutboxRepository);
    }

    private ProblemEventOutbox createPendingOutbox() {
        return ProblemEventOutbox.createPending(
                eventId,
                problemId,
                1,
                "{\"problemId\":\"" + problemId + "\"}",
                Instant.parse("2026-09-21T00:00:00Z")
        );
    }
}