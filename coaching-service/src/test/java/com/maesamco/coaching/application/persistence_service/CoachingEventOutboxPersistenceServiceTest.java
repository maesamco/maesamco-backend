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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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

    /** 이슈 #261 — Relay가 claimPublishable()로 이미 선점해둔 상태를 흉내낸다. */
    private CoachingEventOutbox claimedOutbox(UUID claimId) {
        CoachingEventOutbox outbox = CoachingEventOutbox.create(UUID.randomUUID(), "CoachingCompleted", payload());
        Instant claimedAt = Instant.now();
        outbox.claimForPublish(claimId, claimedAt, claimedAt.plus(5, ChronoUnit.MINUTES));
        return outbox;
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("id로 다시 조회한 fresh entity를 COMPLETED로 표시하고 저장한다")
        void marksOutboxCompleted() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            // Facade가 relay() 루프 시작 시점에 조회해뒀던 참조가 아니라, DB에 실제로 남아있는
            // (오염되지 않은) 값을 흉내낸 별도 인스턴스.
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.markPublished(outboxId, claimId);

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1);
            assertThat(freshOutbox.getClaimId()).isNull(); // 종료 처리 시 선점 해제
            verify(coachingEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("id로 다시 조회했는데 Outbox가 없으면 IllegalStateException을 던진다")
        void throwsWhenOutboxMissing() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> coachingEventOutboxPersistenceService.markPublished(outboxId, claimId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("다른 Relay 실행이 이미 종료 처리한(IN_PROGRESS가 아닌) Outbox는 멱등하게 무시하고 덮어쓰지 않는다")
        void ignoresAlreadyTerminalOutbox() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            freshOutbox.incrementAttemptCount();
            freshOutbox.markFailed(); // 이미 다른 Relay 실행이 FAILED로 끝낸 상황을 재현
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.markPublished(outboxId, claimId);

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.FAILED); // COMPLETED로 덮어쓰지 않음
            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1);
            verify(coachingEventOutboxRepository, never()).save(freshOutbox);
        }

        @Test
        @DisplayName("이슈 #261 — lease 만료 후 다른 Worker가 재선점한(claimId가 다른) Outbox는 무시한다")
        void ignoresOutboxReclaimedByAnotherWorker() {
            UUID outboxId = UUID.randomUUID();
            UUID staleClaimId = UUID.randomUUID();
            UUID currentClaimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(currentClaimId); // 다른 Worker가 이미 재선점함
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.markPublished(outboxId, staleClaimId); // 원래(만료된) claimId로 호출

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS); // 새 Worker의 선점을 건드리지 않음
            verify(coachingEventOutboxRepository, never()).save(freshOutbox);
        }

        @Test
        @DisplayName("저장 시 낙관적 락 충돌(다른 Relay가 거의 동시에 먼저 처리)이 나면 예외를 삼키지 않고 그대로 전파한다")
        void propagatesOptimisticLockingConflict() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));
            willThrow(new ObjectOptimisticLockingFailureException(CoachingEventOutbox.class, outboxId))
                    .given(coachingEventOutboxRepository).save(freshOutbox);

            assertThatThrownBy(() -> coachingEventOutboxPersistenceService.markPublished(outboxId, claimId))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }
    }

    @Nested
    @DisplayName("recordFailedAttempt")
    class RecordFailedAttempt {

        @Test
        @DisplayName("상한 미만이면 attemptCount만 증가시켜 PENDING 상태로 저장한다")
        void staysPendingUnderThreshold() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordFailedAttempt(outboxId, claimId);

            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1);
            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(freshOutbox.getClaimId()).isNull(); // 재시도를 위해 선점 해제
            assertThat(freshOutbox.getNextAttemptAt()).isNotNull(); // 지수 백오프로 다음 재시도 시각 기록(head-of-line blocking 방지)
            verify(coachingEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("저장 시 낙관적 락 충돌이 나면 예외를 삼키지 않고 그대로 전파한다")
        void propagatesOptimisticLockingConflict() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));
            willThrow(new ObjectOptimisticLockingFailureException(CoachingEventOutbox.class, outboxId))
                    .given(coachingEventOutboxRepository).save(freshOutbox);

            assertThatThrownBy(() -> coachingEventOutboxPersistenceService.recordFailedAttempt(outboxId, claimId))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("상한 도달로 FAILED 저장 시 낙관적 락 충돌이 나면 예외를 삼키지 않고 그대로 전파한다")
        void propagatesOptimisticLockingConflictWhenTerminating() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            for (int i = 0; i < MAX_RELAY_ATTEMPTS - 1; i++) {
                freshOutbox.incrementAttemptCount();
            }
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));
            willThrow(new ObjectOptimisticLockingFailureException(CoachingEventOutbox.class, outboxId))
                    .given(coachingEventOutboxRepository).save(freshOutbox);

            assertThatThrownBy(() -> coachingEventOutboxPersistenceService.recordFailedAttempt(outboxId, claimId))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("상한(5회)에 도달하면 Outbox를 FAILED로 종료 처리한다 — Kafka 발행 자체가 실패해 이벤트가 전달되지 않았으므로 안전하다")
        void terminatesWhenThresholdReached() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            for (int i = 0; i < MAX_RELAY_ATTEMPTS - 1; i++) {
                freshOutbox.incrementAttemptCount(); // 이전에 4번 실패했던 상황을 재현
            }
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordFailedAttempt(outboxId, claimId); // 5번째 실패

            assertThat(freshOutbox.getAttemptCount()).isEqualTo(MAX_RELAY_ATTEMPTS);
            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(freshOutbox.getClaimId()).isNull();
            verify(coachingEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("id로 다시 조회했는데 Outbox가 없으면 IllegalStateException을 던진다")
        void throwsWhenOutboxMissing() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> coachingEventOutboxPersistenceService.recordFailedAttempt(outboxId, claimId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("다른 Relay 실행이 이미 종료 처리한(IN_PROGRESS가 아닌) Outbox는 멱등하게 무시하고 덮어쓰지 않는다")
        void ignoresAlreadyTerminalOutbox() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            freshOutbox.incrementAttemptCount();
            freshOutbox.markPublished(); // 이미 다른 Relay 실행이 COMPLETED로 끝낸 상황을 재현
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordFailedAttempt(outboxId, claimId);

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED); // FAILED로 되돌리지 않음
            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1); // 추가로 증가하지 않음
            verify(coachingEventOutboxRepository, never()).save(freshOutbox);
        }
    }

    @Nested
    @DisplayName("recordPostPublishFailure")
    class RecordPostPublishFailure {

        @Test
        @DisplayName("전달받은 id로 Outbox를 새로 조회해서 재시도 카운트를 반영한다")
        void reloadsFreshOutboxById() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            // Facade가 넘겨준 참조가 아니라, DB에 실제로 남아있는(오염되지 않은) 값을 흉내낸 별도 인스턴스.
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId, claimId);

            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1);
            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(freshOutbox.getNextAttemptAt()).isNotNull(); // 지수 백오프로 다음 재시도 시각 기록(head-of-line blocking 방지)
            verify(coachingEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("저장 시 낙관적 락 충돌이 나면 예외를 삼키지 않고 그대로 전파한다")
        void propagatesOptimisticLockingConflict() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));
            willThrow(new ObjectOptimisticLockingFailureException(CoachingEventOutbox.class, outboxId))
                    .given(coachingEventOutboxRepository).save(freshOutbox);

            assertThatThrownBy(() -> coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId, claimId))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("id로 다시 조회했는데 Outbox가 없으면 IllegalStateException을 던진다")
        void throwsWhenOutboxMissing() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId, claimId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("재시도 상한을 넘겨도 FAILED로 종료하지 않는다 — Kafka 발행 자체는 성공했을 수 있어 오분류하면 안 된다")
        void neverTerminatesEvenPastThreshold() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            for (int i = 0; i < MAX_RELAY_ATTEMPTS + 5; i++) {
                freshOutbox.incrementAttemptCount();
            }
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId, claimId);

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            verify(coachingEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("다른 Relay 실행이 이미 COMPLETED로 끝낸 Outbox를 FAILED로 되돌리지 않는다")
        void ignoresAlreadyCompletedOutbox() {
            UUID outboxId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            CoachingEventOutbox freshOutbox = claimedOutbox(claimId);
            freshOutbox.incrementAttemptCount();
            freshOutbox.markPublished(); // 다른 Relay 실행이 이미 완료 처리한 상황을 재현
            given(coachingEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            coachingEventOutboxPersistenceService.recordPostPublishFailure(outboxId, claimId);

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1); // 추가로 증가하지 않음
            verify(coachingEventOutboxRepository, never()).save(freshOutbox);
        }
    }
}
