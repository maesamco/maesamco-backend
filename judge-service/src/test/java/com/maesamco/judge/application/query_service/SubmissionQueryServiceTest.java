package com.maesamco.judge.application.query_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.query.SubmissionGetQuery;
import com.maesamco.judge.domain.entity.FailureCode;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionResult;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.entity.SubmissionTestResult;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.domain.repository.SubmissionTestResultRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.presentation.response.SubmissionInternalGetResponse;
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

@ExtendWith(MockitoExtension.class)
class SubmissionQueryServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @InjectMocks
    private SubmissionQueryService submissionQueryService;

    private Submission pendingSubmission(UUID id) {
        return Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3,
                "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
    }

    @Nested
    @DisplayName("getSubmissionForInternal")
    class GetSubmissionForInternal {

        @Test
        @DisplayName("완료된 제출은 result와 실패한 테스트케이스 목록을 함께 반환한다")
        void returnsResultAndFailedTestsWhenCompleted() {
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

            SubmissionInternalGetResponse response =
                    submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));

            assertThat(response.status()).isEqualTo(SubmissionStatus.COMPLETED.name());
            assertThat(response.result()).isEqualTo(SubmissionResult.WRONG.name());
            assertThat(response.failedTestSummary()).hasSize(1);
        }

        @Test
        @DisplayName("채점 중인 제출은 result와 failedTestSummary가 null이고 테스트 결과 조회를 하지 않는다")
        void returnsNullResultWhenNotCompleted() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            SubmissionInternalGetResponse response =
                    submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));

            assertThat(response.status()).isEqualTo(SubmissionStatus.RUNNING.name());
            assertThat(response.result()).isNull();
            assertThat(response.failedTestSummary()).isNull();
            verify(submissionTestResultRepository, never()).findBySubmissionIdAndPassedFalse(any());
        }

        @Test
        @DisplayName("실패로 종료된 제출은 failureCode를 함께 반환하고 result는 null이다")
        void returnsFailureCodeWhenFailed() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markFailed(FailureCode.JUDGE0_RESPONSE_FAILURE); // TODO: 실제 '실패 종료' 도메인 메서드명 확인 필요

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            SubmissionInternalGetResponse response =
                    submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));

            assertThat(response.status()).isEqualTo(SubmissionStatus.FAILED.name());
            assertThat(response.failureCode()).isEqualTo(FailureCode.JUDGE0_RESPONSE_FAILURE.name());
            assertThat(response.result()).isNull();
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