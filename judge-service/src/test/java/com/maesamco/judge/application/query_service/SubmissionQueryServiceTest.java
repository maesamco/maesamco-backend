package com.maesamco.judge.application.query_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.query.SubmissionGetQuery;
import com.maesamco.judge.application.result.SubmissionGetResult;
import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.entity.SubmissionTestResult;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.domain.repository.SubmissionTestResultRepository;
import com.maesamco.judge.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubmissionQueryServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @InjectMocks
    private SubmissionQueryService submissionQueryService;

    private final UUID userId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    private Submission pendingSubmission(UUID id) {
        Submission submission = Submission.create(
                userId, problemId, UUID.randomUUID(), 3,
                "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
        // Submission.create()는 실제 JPA persist 없이는 @UuidGenerator가 안 돌아서 id가 null임 —
        // submissionId 응답 필드를 의미 있게 검증하려고 테스트에서 id를 직접 심어줌 (리뷰 반영)
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }

    @Nested
    @DisplayName("getSubmissionForInternal")
    class GetSubmissionForInternal {

        @Test
        @DisplayName("완료된 제출은 Coaching Service가 실제 소비하는 필드를 포함해 응답 계약 전체를 정확한 값으로 반환한다")
        void returnsFullContractWhenCompleted() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();
            submission.markCompleted(SubmissionResult.WRONG, 120, 15360);

            SubmissionTestResult failed = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, false, "expected", null);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(submissionTestResultRepository.findBySubmissionIdAndPassedFalse(submissionId))
                    .willReturn(List.of(failed));

            SubmissionGetResult result =
                    submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));

            assertThat(result.submissionId()).isEqualTo(submissionId);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.problemId()).isEqualTo(problemId);
            assertThat(result.code()).isEqualTo("public class Main {}");
            assertThat(result.status()).isEqualTo(SubmissionStatus.COMPLETED);
            assertThat(result.result()).isEqualTo(SubmissionResult.WRONG);
            assertThat(result.failureCode()).isNull();
            assertThat(result.attemptNo()).isEqualTo(3);
            assertThat(result.failedTestSummary()).hasSize(1);
            assertThat(result.failedTestSummary().get(0).isPublic()).isTrue();
            assertThat(result.failedTestSummary().get(0).errorType()).isNull();
        }

        @Test
        @DisplayName("채점 중인 제출은 result와 failedTestSummary가 비어있고 테스트 결과 조회를 하지 않는다")
        void returnsEmptyResultWhenNotCompleted() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            SubmissionGetResult result =
                    submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));

            assertThat(result.status()).isEqualTo(SubmissionStatus.RUNNING);
            assertThat(result.result()).isNull();
            assertThat(result.failedTestSummary()).isEmpty();
            verify(submissionTestResultRepository, never()).findBySubmissionIdAndPassedFalse(any());
        }

        @Test
        @DisplayName("실패로 종료된 제출은 failureCode를 반환하고 result는 null이며, 테스트 결과 조회를 하지 않는다")
        void returnsFailureCodeWhenFailed() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markFailed(FailureCode.JUDGE0_RESPONSE_FAILURE);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            SubmissionGetResult result =
                    submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));

            assertThat(result.status()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(result.failureCode()).isEqualTo(FailureCode.JUDGE0_RESPONSE_FAILURE);
            assertThat(result.result()).isNull();
            verify(submissionTestResultRepository, never()).findBySubmissionIdAndPassedFalse(any());
        }

        @Test
        @DisplayName("존재하지 않는 제출이면 SUBMISSION_NOT_FOUND 예외를 던진다")
        void throwsWhenSubmissionMissing() {
            UUID submissionId = UUID.randomUUID();
            given(submissionRepository.findById(submissionId)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId)))
                    .isInstanceOf(BusinessException.class);
        }
    }
}