package com.maesamco.coaching.application.persistence_service;

import com.maesamco.coaching.domain.entity.CoachingEventOutbox;
import com.maesamco.coaching.domain.entity.OutboxStatus;
import com.maesamco.coaching.domain.repository.CoachingEventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoachingEventOutboxPersistenceServiceTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final int MAX_RELAY_ATTEMPTS = 5;

    @Mock
    private CoachingEventOutboxRepository coachingEventOutboxRepository;

    @InjectMocks
    private CoachingEventOutboxPersistenceService coachingEventOutboxPersistenceService;

    private ObjectNode payload() {
        return JSON_MAPPER.createObjectNode().put("coachingId", UUID.randomUUID().toString());
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("Outbox를 COMPLETED로 표시하고 저장한다")
        void marksOutboxCompleted() {
            CoachingEventOutbox outbox = CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload());

            coachingEventOutboxPersistenceService.markPublished(outbox);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(outbox.getAttemptCount()).isEqualTo(1);
            verify(coachingEventOutboxRepository).save(outbox);
        }
    }

    @Nested
    @DisplayName("recordFailedAttempt")
    class RecordFailedAttempt {

        @Test
        @DisplayName("상한 미만이면 attemptCount만 증가시켜 PENDING 상태로 저장한다")
        void staysPendingUnderThreshold() {
            CoachingEventOutbox outbox = CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload());

            coachingEventOutboxPersistenceService.recordFailedAttempt(outbox);

            assertThat(outbox.getAttemptCount()).isEqualTo(1);
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            verify(coachingEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("상한(5회)에 도달하면 Outbox를 FAILED로 종료 처리한다 — Kafka 발행 자체가 실패해 이벤트가 전달되지 않았으므로 안전하다")
        void terminatesWhenThresholdReached() {
            CoachingEventOutbox outbox = CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload());
            for (int i = 0; i < MAX_RELAY_ATTEMPTS - 1; i++) {
                outbox.incrementAttemptCount(); // 이전에 4번 실패했던 상황을 재현
            }

            coachingEventOutboxPersistenceService.recordFailedAttempt(outbox); // 5번째 실패

            assertThat(outbox.getAttemptCount()).isEqualTo(MAX_RELAY_ATTEMPTS);
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            verify(coachingEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("다른 Relay 실행이 이미 종료 처리한(PENDING이 아닌) Outbox는 멱등하게 무시한다")
        void ignoresAlreadyTerminalOutbox() {
            CoachingEventOutbox outbox = CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload());
            outbox.incrementAttemptCount();
            outbox.markPublished(); // 이미 다른 Relay 실행이 COMPLETED로 끝낸 상황을 재현

            coachingEventOutboxPersistenceService.recordFailedAttempt(outbox);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(outbox.getAttemptCount()).isEqualTo(1); // 추가로 증가하지 않음
            verify(coachingEventOutboxRepository, never()).save(outbox);
        }
    }

    @Nested
    @DisplayName("recordPostPublishFailure")
    class RecordPostPublishFailure {

        @Test
        @DisplayName("전달받은 id로 Outbox를 새로 조회해서 재시도 카운트를 반영한다")
        void reloadsFreshOutboxById() {
            UUID outboxId = UUID.randomUUID();
            // Facade가 넘겨준 참조가 아니라, DB에 실제로 남아있는(오염되지 않은) 값을 흉내낸 별도 인스턴스.
            CoachingEventOutbox freshOutbox = CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload());
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId);

            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1);
            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            verify(coachingEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("id로 다시 조회했는데 Outbox가 없으면 IllegalStateException을 던진다")
        void throwsWhenOutboxMissing() {
            UUID outboxId = UUID.randomUUID();
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("재시도 상한을 넘겨도 FAILED로 종료하지 않는다 — Kafka 발행 자체는 성공했을 수 있어 오분류하면 안 된다")
        void neverTerminatesEvenPastThreshold() {
            UUID outboxId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload());
            for (int i = 0; i < MAX_RELAY_ATTEMPTS + 5; i++) {
                freshOutbox.incrementAttemptCount();
            }
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId);

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            verify(coachingEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("다른 Relay 실행이 이미 COMPLETED로 끝낸 Outbox를 FAILED로 되돌리지 않는다")
        void ignoresAlreadyCompletedOutbox() {
            UUID outboxId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload());
            freshOutbox.incrementAttemptCount();
            freshOutbox.markPublished(); // 다른 Relay 실행이 이미 완료 처리한 상황을 재현
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId);

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1); // 추가로 증가하지 않음
            verify(coachingEventOutboxRepository, never()).save(freshOutbox);
        }
    }
}
