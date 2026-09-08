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
    }

    @Nested
    @DisplayName("recordPostPublishFailure")
    class RecordPostPublishFailure {

        @Test
        @DisplayName("전달받은 id로 Outbox를 새로 조회해서 재시도 카운트를 반영한다")
        void reloadsFreshOutboxById() {
            UUID outboxId = UUID.randomUUID();
            // Facade가 넘겨준 참조가 아니라, DB에 실제로 남아있는(오염되지 않은) 값을 흉내낸 별도 인스턴스.
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
    }
}