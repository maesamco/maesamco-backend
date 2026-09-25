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
import java.time.Instant;
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

    private final UUID claimId = UUID.randomUUID();

    /** claimId로 선점(IN_PROGRESS)한 Outbox를 만들고, findById가 그 객체를 돌려주도록 스텁한다. */
    private SubmissionEventOutbox claimedOutbox(UUID aggregateId, String eventType) {
        SubmissionEventOutbox outbox = SubmissionEventOutbox.create(aggregateId, eventType, "{}");
        outbox.claimForPublish(claimId, Instant.now(), Instant.now().plusSeconds(300));
        given(submissionEventOutboxRepository.findById(outbox.getId())).willReturn(Optional.of(outbox));
        return outbox;
    }

    private Submission queuableSubmission(UUID id) {
        return Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("Outbox를 COMPLETED로 표시하고 선점을 풀며 연결된 Submission을 QUEUED로 전이시킨다")
        void marksOutboxCompletedAndSubmissionQueued() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = claimedOutbox(submissionId, "JudgeRequested");
            Submission submission = queuableSubmission(submissionId);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            boolean updated = submissionEventOutboxPersistenceService.markPublished(outbox.getId(), claimId);

            assertThat(updated).isTrue();
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(outbox.getClaimId()).isNull();
            assertThat(outbox.getLeaseUntil()).isNull();
            verify(submissionEventOutboxRepository).save(outbox);
            verify(submissionRepository).save(submission);
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.QUEUED);
        }

        @Test
        @DisplayName("Submission이 이미 QUEUED 이후 상태로 넘어가 있으면 예외를 삼키고 넘어간다")
        void swallowsRaceWhenSubmissionAlreadyAdvanced() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = claimedOutbox(submissionId, "JudgeRequested");
            Submission submission = queuableSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            assertThatCode(() -> submissionEventOutboxPersistenceService.markPublished(outbox.getId(), claimId))
                    .doesNotThrowAnyException();
            verify(submissionEventOutboxRepository).save(outbox);
        }

        @Test
        @DisplayName("연결된 Submission이 없으면 SUBMISSION_NOT_FOUND를 던진다")
        void throwsWhenSubmissionMissing() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = claimedOutbox(submissionId, "JudgeRequested");
            given(submissionRepository.findById(submissionId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> submissionEventOutboxPersistenceService.markPublished(outbox.getId(), claimId))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("SubmissionJudged 발행 성공 시에는 Submission 상태를 건드리지 않는다")
        void doesNotTouchSubmissionForSubmissionJudged() {
            SubmissionEventOutbox outbox = claimedOutbox(UUID.randomUUID(), "SubmissionJudged");

            submissionEventOutboxPersistenceService.markPublished(outbox.getId(), claimId);

            verify(submissionEventOutboxRepository).save(outbox);
            verify(submissionRepository, never()).findById(any());
            verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("이슈 #272 — claimId가 다르면(lease 만료 후 다른 Worker가 재선점) 아무것도 바꾸지 않고 false를 돌려준다")
        void ignoresStaleClaim() {
            SubmissionEventOutbox outbox = claimedOutbox(UUID.randomUUID(), "JudgeRequested");

            boolean updated = submissionEventOutboxPersistenceService.markPublished(outbox.getId(), UUID.randomUUID());

            assertThat(updated).isFalse();
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
            verify(submissionEventOutboxRepository, never()).save(any());
            verify(submissionRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("recordFailedAttempt")
    class RecordFailedAttempt {

        @Test
        @DisplayName("상한 미만이면 attemptCount를 올리고 선점을 풀어 PENDING으로 되돌리며 Submission은 건드리지 않는다")
        void releasesClaimUnderThreshold() {
            SubmissionEventOutbox outbox = claimedOutbox(UUID.randomUUID(), "JudgeRequested");

            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), claimId);

            assertThat(outbox.getAttemptCount()).isEqualTo(1);
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outbox.getClaimId()).isNull();
            verify(submissionEventOutboxRepository).save(outbox);
            verify(submissionRepository, never()).findById(any());
        }

        @Test
        @DisplayName("상한(5회)에 도달하면 Outbox를 FAILED로, Submission을 KAFKA_PROCESSING_FAILURE로 종료 처리한다")
        void terminatesWhenThresholdReached() {
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = claimedOutbox(submissionId, "JudgeRequested");
            for (int i = 0; i < MAX_RELAY_ATTEMPTS - 1; i++) {
                outbox.incrementAttemptCount(); // 이전에 4번 실패했던 상황을 재현
            }
            Submission submission = queuableSubmission(submissionId);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), claimId); // 5번째 실패

            assertThat(outbox.getAttemptCount()).isEqualTo(MAX_RELAY_ATTEMPTS);
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(outbox.getClaimId()).isNull();
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(submission.getFailureCode()).isEqualTo(FailureCode.KAFKA_PROCESSING_FAILURE);
        }

        @Test
        @DisplayName("이미 종료 상태(COMPLETED)인 Outbox면 아무것도 하지 않고 멱등하게 종료한다")
        void skipsWhenOutboxAlreadyTerminated() {
            SubmissionEventOutbox outbox = claimedOutbox(UUID.randomUUID(), "JudgeRequested");
            outbox.markPublished(); // 다른 Relay가 먼저 COMPLETED 처리해둔 상황을 재현

            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), claimId);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(outbox.getAttemptCount()).isZero();
            verify(submissionEventOutboxRepository, never()).save(any());
            verify(submissionRepository, never()).findById(any());
        }

        @Test
        @DisplayName("이슈 #272 — claimId가 다르면 다른 Worker의 선점을 건드리지 않는다")
        void ignoresStaleClaim() {
            SubmissionEventOutbox outbox = claimedOutbox(UUID.randomUUID(), "JudgeRequested");

            submissionEventOutboxPersistenceService.recordFailedAttempt(outbox.getId(), UUID.randomUUID());

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
            assertThat(outbox.getAttemptCount()).isZero();
            verify(submissionEventOutboxRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("recordPostPublishFailure")
    class RecordPostPublishFailure {

        @Test
        @DisplayName("id로 Outbox를 새로 조회해서 재시도 카운트를 반영하고 선점을 풀어 PENDING으로 되돌린다")
        void reloadsFreshOutboxById() {
            SubmissionEventOutbox freshOutbox = claimedOutbox(UUID.randomUUID(), "JudgeRequested");

            submissionEventOutboxPersistenceService.recordPostPublishFailure(freshOutbox.getId(), claimId);

            assertThat(freshOutbox.getAttemptCount()).isEqualTo(1);
            assertThat(freshOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(freshOutbox.getClaimId()).isNull();
            verify(submissionEventOutboxRepository).save(freshOutbox);
        }

        @Test
        @DisplayName("id로 다시 조회했는데 Outbox가 없으면 IllegalStateException을 던진다")
        void throwsWhenOutboxMissing() {
            UUID outboxId = UUID.randomUUID();
            given(submissionEventOutboxRepository.findById(outboxId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> submissionEventOutboxPersistenceService.recordPostPublishFailure(outboxId, claimId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("상한(5회)에 도달해도 Kafka 발행 자체는 성공했으므로 Outbox만 FAILED로 종료하고 "
                + "Submission은 건드리지 않는다")
        void terminatesOutboxOnlyWithoutTouchingSubmissionWhenThresholdReached() {
            SubmissionEventOutbox freshOutbox = claimedOutbox(UUID.randomUUID(), "JudgeRequested");
            for (int i = 0; i < MAX_POST_PUBLISH_FAILURE_ATTEMPTS - 1; i++) {
                freshOutbox.incrementAttemptCount(); // 앞서 4번 DB 후처리가 실패했던 상황을 재현
            }

            submissionEventOutboxPersistenceService.recordPostPublishFailure(freshOutbox.getId(), claimId); // 5번째 실패

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
            SubmissionEventOutbox freshOutbox = claimedOutbox(UUID.randomUUID(), "JudgeRequested");
            freshOutbox.markFailed(); // 다른 경로로 이미 FAILED 처리된 상황을 재현

            submissionEventOutboxPersistenceService.recordPostPublishFailure(freshOutbox.getId(), claimId);

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
            SubmissionEventOutbox outbox = claimedOutbox(submissionId, "UnknownEvent");
            Submission submission = queuableSubmission(submissionId);
            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            submissionEventOutboxPersistenceService.markUnsupportedEventType(outbox.getId(), claimId);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(outbox.getAttemptCount()).isZero();
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(submission.getFailureCode()).isEqualTo(FailureCode.INTERNAL_SYSTEM_ERROR);
        }

        @Test
        @DisplayName("이미 종료 상태(COMPLETED)인 Outbox면 아무것도 하지 않고 멱등하게 종료한다")
        void skipsWhenOutboxAlreadyTerminated() {
            SubmissionEventOutbox outbox = claimedOutbox(UUID.randomUUID(), "UnknownEvent");
            outbox.markPublished(); // 다른 경로로 이미 COMPLETED 처리된 상황을 재현

            submissionEventOutboxPersistenceService.markUnsupportedEventType(outbox.getId(), claimId);

            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
            assertThat(outbox.getAttemptCount()).isZero();
            verify(submissionEventOutboxRepository, never()).save(any());
            verify(submissionRepository, never()).findById(any());
        }
    }
}
