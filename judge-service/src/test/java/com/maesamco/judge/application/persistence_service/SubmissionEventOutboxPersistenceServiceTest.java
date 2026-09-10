package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.OutboxStatus;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmissionEventOutboxPersistenceServiceTest {

    @Mock
    private SubmissionEventOutboxRepository submissionEventOutboxRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private SubmissionEventOutboxPersistenceService submissionEventOutboxPersistenceService;

    private static final int MAX_RELAY_ATTEMPTS = 5;
    private static final int MAX_POST_PUBLISH_FAILURE_ATTEMPTS = 5;

    private Submission queuableSubmission(UUID id) {
        return Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("Outbox를 COMPLETED로 표시하고 연결된 Submission을 QUEUED로 전이시킨다")
        void marksOutboxCompletedAndSubmissionQueued() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(submissionId, "JudgeRequested", "{}");
            Submission submission = queuableSubmission(submissionId);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            submissionEventOutboxPersistenceService.markPublished(outbox);

            verify(submissionEventOutboxRepository).save(outbox);
            verify(submissionRepository).save(submission);
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.QUEUED);
        }

        @Test
        @DisplayName("Submission이 이미 QUEUED 이후 상태로 넘어가 있으면 예외를 삼키고 넘어간다")
        void swallowsRaceWhenSubmissionAlreadyAdvanced() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(submissionId, "JudgeRequested", "{}");
            Submission submission = queuableSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            assertThatCode(() -> submissionEventOutboxPersistenceService.markPublished(outbox))
                    .doesNotThrowAnyException();
            verify(submissionEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("연결된 Submission이 없으면 SUBMISSION_NOT_FOUND를 던진다")
        void throwsWhenSubmissionMissing() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(submissionId, "JudgeRequested", "{}");
            given(submissionRepository.findById(submissionId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> submissionEventOutboxPersistenceService.markPublished(outbox))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("recordFailedAttempt")
    class RecordFailedAttempt {

        @Test
        @DisplayName("상한 미만이면 attemptCount만 증가시켜 PENDING 상태로 저장하고 Submission은 건드리지 않는다")
        void staysPendingUnderThreshold() {
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(UUID.randomUUID(), "JudgeRequested", "{}");

            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox);

            assertThat(outbox.getAttemptCount()).isEqualTo(1);
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            verify(submissionEventOutboxRepository).save(outbox);
            verify(submissionRepository, never()).findById(any());
        }

        @Test
        @DisplayName("상한(5회)에 도달하면 Outbox를 FAILED로, Submission을 KAFKA_PROCESSING_FAILURE로 종료 처리한다")
        void terminatesWhenThresholdReached() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(submissionId, "JudgeRequested", "{}");
            for (int i = 0; i < MAX_RELAY_ATTEMPTS - 1; i++) {
                outbox.incrementAttemptCount(); // 이전에 4번 실패했던 상황을 재현
            }
            Submission submission = queuableSubmission(submissionId);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox); // 5번째 실패

            assertThat(outbox.getAttemptCount()).isEqualTo(MAX_RELAY_ATTEMPTS);
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(submission.getFailureCode()).isEqualTo(FailureCode.KAFKA_PROCESSING_FAILURE);
        }

        @Test
        @DisplayName("이미 종료 상태(COMPLETED)인 Outbox면 아무것도 하지 않고 멱등하게 종료한다")
        void skipsWhenOutboxAlreadyTerminated() {
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(UUID.randomUUID(), "JudgeRequested", "{}");
            outbox.markPublished(); // 다른 Relay가 먼저 COMPLETED 처리해둔 상황을 재현

            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(outbox.getAttemptCount()).isZero();
            verify(submissionEventOutboxRepository, never()).save(any());
            verify(submissionRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("recordPostPublishFailure")
    class RecordPostPublishFailure {

        @Test
        @DisplayName("전달받은 id로 Outbox를 새로 조회해서 재시도 카운트를 반영한다")
        void reloadsFreshOutboxById() {
            UUID outboxId = UUID.randomUUID();
            SubmissionEventOutbox freshOutbox = SubmissionEventOutbox.create(UUID.randomUUID(), "JudgeRequested", "{}");
            given(submissionEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            submissionEventOutboxPersistenceService.recordPostPublishFailure(outboxId);

            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1);
            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            verify(submissionEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("id로 다시 조회했는데 Outbox가 없으면 IllegalStateException을 던진다")
        void throwsWhenOutboxMissing() {
            UUID outboxId = UUID.randomUUID();
            given(submissionEventOutboxRepository.findById(outboxId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> submissionEventOutboxPersistenceService.recordPostPublishFailure(outboxId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("상한(5회)에 도달해도 Kafka 발행 자체는 성공했으므로 Outbox만 FAILED로 종료하고 "
                + "Submission은 건드리지 않는다")
        void terminatesOutboxOnlyWithoutTouchingSubmissionWhenThresholdReached() {
            UUID outboxId = UUID.randomUUID();
            SubmissionEventOutbox freshOutbox = SubmissionEventOutbox.create(UUID.randomUUID(), "JudgeRequested", "{}");
            for (int i = 0; i < MAX_POST_PUBLISH_FAILURE_ATTEMPTS - 1; i++) {
                freshOutbox.incrementAttemptCount(); // 앞서 4번 DB 후처리가 실패했던 상황을 재현
            }
            given(submissionEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            submissionEventOutboxPersistenceService.recordPostPublishFailure(outboxId); // 5번째 실패

            assertThat(freshOutbox.getAttemptCount()).isEqualTo(MAX_POST_PUBLISH_FAILURE_ATTEMPTS);
            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            verify(submissionEventOutboxRepository).save(freshOutbox);

            // 핵심 검증 — Kafka 발행 자체는 성공했으므로 Submission을 실패로 종료하면 안 된다
            verify(submissionRepository, never()).findById(any());
            verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("재조회한 Outbox가 이미 종료 상태(FAILED)면 아무것도 하지 않고 멱등하게 종료한다")
        void skipsWhenReloadedOutboxAlreadyTerminated() {
            UUID outboxId = UUID.randomUUID();
            SubmissionEventOutbox freshOutbox = SubmissionEventOutbox.create(UUID.randomUUID(), "JudgeRequested", "{}");
            freshOutbox.markFailed(); // 다른 경로로 이미 FAILED 처리된 상황을 재현
            given(submissionEventOutboxRepository.findById(outboxId)).willReturn(Optional.of(freshOutbox));

            submissionEventOutboxPersistenceService.recordPostPublishFailure(outboxId);

            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(freshOutbox.getAttemptCount()).isZero();
            verify(submissionEventOutboxRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("markUnsupportedEventType")
    class MarkUnsupportedEventType {

        @Test
        @DisplayName("재시도 없이 즉시 Outbox를 FAILED로, Submission을 INTERNAL_SYSTEM_ERROR로 종료 처리한다")
        void terminatesImmediately() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(submissionId, "SubmissionJudged", "{}");
            Submission submission = queuableSubmission(submissionId);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            submissionEventOutboxPersistenceService.markUnsupportedEventType(outbox);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(outbox.getAttemptCount()).isZero(); // 재시도 카운트를 소진시킨 게 아니라 즉시 종료된 것
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(submission.getFailureCode()).isEqualTo(FailureCode.INTERNAL_SYSTEM_ERROR);
        }

        @Test
        @DisplayName("이미 종료 상태(COMPLETED)인 Outbox면 아무것도 하지 않고 멱등하게 종료한다")
        void skipsWhenOutboxAlreadyTerminated() {
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(UUID.randomUUID(), "SubmissionJudged", "{}");
            outbox.markPublished(); // 다른 경로로 이미 COMPLETED 처리된 상황을 재현

            submissionEventOutboxPersistenceService.markUnsupportedEventType(outbox);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            verify(submissionEventOutboxRepository, never()).save(any());
            verify(submissionRepository, never()).findById(any());
        }
    }
}