package com.maesamco.judge.application.facade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.persistence_service.SubmissionEventOutboxPersistenceService;
import com.maesamco.judge.application.port.EventPublisherPort;
import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubmissionEventRelayFacadeTest {

    @Mock
    private SubmissionEventOutboxRepository submissionEventOutboxRepository;

    @Mock
    private SubmissionEventOutboxPersistenceService submissionEventOutboxPersistenceService;

    @Mock
    private EventPublisherPort eventPublisherPort;

    @InjectMocks
    private SubmissionEventRelayFacade submissionEventRelayFacade;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(submissionEventRelayFacade, "judgeRequestedTopic", "judge-requested");
    }

    private SubmissionEventOutbox pendingOutbox(String eventType) {
        return SubmissionEventOutbox.create(UUID.randomUUID(), eventType, "{\"submissionId\":\"...\"}");
    }

    @Nested
    @DisplayName("relay")
    class Relay {

        @Test
        @DisplayName("PENDING Outbox 발행에 성공하면 markPublished를 호출한다")
        void marksPublishedOnSuccess() {
            SubmissionEventOutbox outbox = pendingOutbox("JudgeRequested");
            given(submissionEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                    .willReturn(List.of(outbox));

            submissionEventRelayFacade.relay();

            verify(eventPublisherPort).publish("judge-requested", outbox.getAggregateId().toString(), outbox.getPayload());
            verify(submissionEventOutboxPersistenceService).markPublished(outbox);
            verify(submissionEventOutboxPersistenceService, never()).recordFailedAttempt(any());
        }

        @Test
        @DisplayName("발행이 실패하면 recordFailedAttempt를 호출하고 예외를 전파하지 않는다")
        void recordsFailedAttemptOnPublishFailure() {
            SubmissionEventOutbox outbox = pendingOutbox("JudgeRequested");
            given(submissionEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                    .willReturn(List.of(outbox));
            willThrow(new IllegalStateException("Kafka 발행 실패"))
                    .given(eventPublisherPort).publish(anyString(), anyString(), anyString());

            submissionEventRelayFacade.relay();

            verify(submissionEventOutboxPersistenceService).recordFailedAttempt(outbox);
            verify(submissionEventOutboxPersistenceService, never()).markPublished(any());
        }

        @Test
        @DisplayName("알 수 없는 event_type이면 발행을 시도하지 않고 스킵한다")
        void skipsUnknownEventType() {
            SubmissionEventOutbox outbox = pendingOutbox("SubmissionJudged"); // 이슈 9 몫, 아직 미지원

            given(submissionEventOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                    .willReturn(List.of(outbox));

            submissionEventRelayFacade.relay();

            verify(eventPublisherPort, never()).publish(anyString(), anyString(), anyString());
            verify(submissionEventOutboxPersistenceService, never()).markPublished(any());
            verify(submissionEventOutboxPersistenceService, never()).recordFailedAttempt(any());
        }
    }
}