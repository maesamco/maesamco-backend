package com.maesamco.judge.application.facade;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.persistence_service.SubmissionEventOutboxPersistenceService;
import com.maesamco.judge.application.port.EventPublisherPort;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class SubmissionEventRelayFacadeTest {

    @Mock
    private SubmissionEventOutboxPersistenceService submissionEventOutboxPersistenceService;

    @Mock
    private EventPublisherPort eventPublisherPort;

    private SubmissionEventRelayFacade submissionEventRelayFacade;

    @BeforeEach
    void setUp() {
        submissionEventRelayFacade = new SubmissionEventRelayFacade(
                submissionEventOutboxPersistenceService, eventPublisherPort,
                "judge-requested", "submission-judged", 300_000L, 5_000L);
    }

    /** 선점된 Outbox 한 건 다음에는 더 없음을 돌려주도록 claimNext를 스텁한다. */
    private void givenClaimable(SubmissionEventOutbox outbox) {
        given(submissionEventOutboxPersistenceService.claimNext(any(UUID.class), any(Duration.class)))
                .willReturn(Optional.of(outbox))
                .willReturn(Optional.empty());
    }

    private SubmissionEventOutbox claimedOutbox(String eventType) {
        SubmissionEventOutbox outbox = SubmissionEventOutbox.create(UUID.randomUUID(), eventType, "{\"submissionId\":\"...\"}");
        outbox.claimForPublish(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(300));
        return outbox;
    }

    private UUID capturedClaimId() {
        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(submissionEventOutboxPersistenceService, times(2)).claimNext(captor.capture(), any(Duration.class));
        return captor.getAllValues().get(0);
    }

    @Nested
    @DisplayName("생성")
    class Construction {

        @Test
        @DisplayName("이슈 #272 — lease가 발행 타임아웃보다 충분히(1초 이상) 길지 않으면 생성에 실패한다")
        void rejectsLeaseShorterThanPublishTimeout() {
            assertThatThrownBy(() -> new SubmissionEventRelayFacade(
                    submissionEventOutboxPersistenceService, eventPublisherPort,
                    "judge-requested", "submission-judged", 5_500L, 5_000L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("relay")
    class Relay {

        @Test
        @DisplayName("선점한 Outbox 발행에 성공하면 선점 claimId로 markPublished를 호출한다")
        void marksPublishedOnSuccess() {
            SubmissionEventOutbox outbox = claimedOutbox("JudgeRequested");
            givenClaimable(outbox);

            submissionEventRelayFacade.relay();

            verify(eventPublisherPort).publish("judge-requested", outbox.getAggregateId().toString(), outbox.getPayload());
            UUID claimId = capturedClaimId();
            verify(submissionEventOutboxPersistenceService).markPublished(outbox.getId(), claimId);
            verify(submissionEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any());
        }

        @Test
        @DisplayName("선점할 Outbox가 없으면 발행하지 않는다")
        void doesNothingWhenNothingClaimable() {
            given(submissionEventOutboxPersistenceService.claimNext(any(UUID.class), any(Duration.class)))
                    .willReturn(Optional.empty());

            submissionEventRelayFacade.relay();

            verify(eventPublisherPort, never()).publish(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("발행이 실패하면 recordFailedAttempt를 호출하고 예외를 전파하지 않는다")
        void recordsFailedAttemptOnPublishFailure() {
            SubmissionEventOutbox outbox = claimedOutbox("JudgeRequested");
            givenClaimable(outbox);
            willThrow(new IllegalStateException("Kafka 발행 실패"))
                    .given(eventPublisherPort).publish(anyString(), anyString(), anyString());

            submissionEventRelayFacade.relay();

            UUID claimId = capturedClaimId();
            verify(submissionEventOutboxPersistenceService).recordFailedAttempt(outbox.getId(), claimId);
            verify(submissionEventOutboxPersistenceService, never()).markPublished(any(), any());
        }

        @Test
        @DisplayName("알 수 없는 event_type이면 발행을 시도하지 않고 즉시 markUnsupportedEventType으로 종료 처리한다")
        void marksUnsupportedEventTypeWithoutPublishing() {
            SubmissionEventOutbox outbox = claimedOutbox("UnknownEvent");
            givenClaimable(outbox);

            submissionEventRelayFacade.relay();

            verify(eventPublisherPort, never()).publish(anyString(), anyString(), anyString());
            verify(submissionEventOutboxPersistenceService, never()).markPublished(any(), any());
            verify(submissionEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any());
            UUID claimId = capturedClaimId();
            verify(submissionEventOutboxPersistenceService).markUnsupportedEventType(outbox.getId(), claimId);
        }

        @Test
        @DisplayName("발행은 성공했지만 markPublished 후처리가 실패하면 recordPostPublishFailure로 넘긴다")
        void recordsPostPublishFailureWhenMarkPublishedFails() {
            SubmissionEventOutbox outbox = claimedOutbox("JudgeRequested");
            givenClaimable(outbox);
            willThrow(new RuntimeException("DB 후처리 실패"))
                    .given(submissionEventOutboxPersistenceService).markPublished(any(), any());

            submissionEventRelayFacade.relay();

            verify(eventPublisherPort).publish("judge-requested", outbox.getAggregateId().toString(), outbox.getPayload());
            UUID claimId = capturedClaimId();
            verify(submissionEventOutboxPersistenceService).recordPostPublishFailure(outbox.getId(), claimId);
            verify(submissionEventOutboxPersistenceService, never()).recordFailedAttempt(any(), any());
        }

        @Test
        @DisplayName("SubmissionJudged Outbox는 submission-judged 토픽으로 발행하고 markPublished를 호출한다")
        void marksPublishedForSubmissionJudged() {
            SubmissionEventOutbox outbox = claimedOutbox("SubmissionJudged");
            givenClaimable(outbox);

            submissionEventRelayFacade.relay();

            verify(eventPublisherPort).publish("submission-judged", outbox.getAggregateId().toString(), outbox.getPayload());
            UUID claimId = capturedClaimId();
            verify(submissionEventOutboxPersistenceService).markPublished(outbox.getId(), claimId);
            verify(submissionEventOutboxPersistenceService, never()).markUnsupportedEventType(any(), any());
        }

        @Test
        @DisplayName("이슈 #272 — 후처리에서 낙관적 락 충돌이 나도 배치가 죽지 않고 다음 항목을 계속 처리한다")
        void continuesBatchOnOptimisticLockConflict() {
            SubmissionEventOutbox first = claimedOutbox("JudgeRequested");
            SubmissionEventOutbox second = claimedOutbox("JudgeRequested");
            given(submissionEventOutboxPersistenceService.claimNext(any(UUID.class), any(Duration.class)))
                    .willReturn(Optional.of(first))
                    .willReturn(Optional.of(second))
                    .willReturn(Optional.empty());
            willThrow(new ObjectOptimisticLockingFailureException(SubmissionEventOutbox.class, first.getId()))
                    .given(submissionEventOutboxPersistenceService).markPublished(any(), any());
            willThrow(new ObjectOptimisticLockingFailureException(SubmissionEventOutbox.class, first.getId()))
                    .given(submissionEventOutboxPersistenceService).recordPostPublishFailure(any(), any());

            submissionEventRelayFacade.relay();

            verify(eventPublisherPort, times(2)).publish(anyString(), anyString(), anyString());
        }
    }
}
