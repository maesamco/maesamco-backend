package com.maesamco.judge.application.persistence_service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
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

    private Submission queuableSubmission(UUID id) {
        Submission submission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
        return submission;
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("Outbox를 COMPLETED로 표시하고 연결된 Submission을 QUEUED로 전이시킨다")
        void marksOutboxCompletedAndSubmissionQueued() {
            // given
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(submissionId, "JudgeRequested", "{}");
            Submission submission = queuableSubmission(submissionId);
            given(submissionRepository.findById(submissionId)).willReturn(java.util.Optional.of(submission));

            // when
            submissionEventOutboxPersistenceService.markPublished(outbox);

            // then
            verify(submissionEventOutboxRepository).save(outbox);
            verify(submissionRepository).save(submission);
            org.assertj.core.api.Assertions.assertThat(submission.getStatus())
                    .isEqualTo(com.maesamco.judge.domain.entity.SubmissionStatus.QUEUED);
        }

        @Test
        @DisplayName("Submission이 이미 QUEUED 이후 상태로 넘어가 있으면 예외를 삼키고 넘어간다")
        void swallowsRaceWhenSubmissionAlreadyAdvanced() {
            // given
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(submissionId, "JudgeRequested", "{}");
            Submission submission = queuableSubmission(submissionId);
            submission.markQueued();
            submission.markRunning(); // 이미 RUNNING까지 가버린 상태를 흉내냄
            given(submissionRepository.findById(submissionId)).willReturn(java.util.Optional.of(submission));

            // when / then — 예외가 밖으로 새면 Relay가 이 Outbox를 계속 재시도하게 됨(중복 발행 유발)
            assertThatCode(() -> submissionEventOutboxPersistenceService.markPublished(outbox))
                    .doesNotThrowAnyException();
            verify(submissionEventOutboxRepository).save(outbox); // Outbox는 그래도 COMPLETED 확정
        }

        @Test
        @DisplayName("연결된 Submission이 없으면 SUBMISSION_NOT_FOUND를 던진다")
        void throwsWhenSubmissionMissing() {
            // given
            UUID submissionId = UUID.randomUUID();
            SubmissionEventOutbox outbox = SubmissionEventOutbox.create(submissionId, "JudgeRequested", "{}");
            given(submissionRepository.findById(submissionId)).willReturn(java.util.Optional.empty());

            // when / then
            assertThatThrownBy(() -> submissionEventOutboxPersistenceService.markPublished(outbox))
                    .isInstanceOf(BusinessException.class);
        }
    }
}