package com.maesamco.judge.application.query_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maesamco.judge.application.query.SubmissionGetQuery;
import com.maesamco.judge.application.result.SubmissionExternalGetResult;
import com.maesamco.judge.application.result.SubmissionInternalGetResult;
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
    private final UUID problemVersionId = UUID.randomUUID();

    private Submission pendingSubmission(UUID id) {
        Submission submission = Submission.create(
                userId, problemId, problemVersionId, 3,
                "public class Main {}", SubmissionLanguage.JAVA17, "idem-" + id);
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
                    submissionId, UUID.randomUUID(), true, false, "expected", null,
                    null, null);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(submissionTestResultRepository.findBySubmissionIdAndPassedFalse(submissionId))
                    .willReturn(List.of(failed));

            SubmissionInternalGetResult result =
                    submissionQueryService.getSubmissionForInternal(SubmissionGetQuery.from(submissionId));

            assertThat(result.submissionId()).isEqualTo(submissionId);
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.problemId()).isEqualTo(problemId);
            assertThat(result.problemVersionId()).isEqualTo(problemVersionId);
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

            SubmissionInternalGetResult result =
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

            SubmissionInternalGetResult result =
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

    @Nested
    @DisplayName("getSubmission")
    class GetSubmission {

        @Test
        @DisplayName("본인의 완료된 제출은 testResults를 포함해 전체 필드를 정확한 값으로 반환한다")
        void returnsFullResultWhenOwnedAndCompleted() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();
            submission.markCompleted(SubmissionResult.CORRECT, 120, 15360);

            SubmissionTestResult passed = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), true, true, "8", null, null, null);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                    .willReturn(List.of(passed));

            SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

            assertThat(result.submissionId()).isEqualTo(submissionId);
            assertThat(result.problemVersionId()).isEqualTo(problemVersionId);
            assertThat(result.status()).isEqualTo(SubmissionStatus.COMPLETED);
            assertThat(result.result()).isEqualTo(SubmissionResult.CORRECT);
            assertThat(result.testResults()).hasSize(1);
            assertThat(result.testResults().get(0).passed()).isTrue();
            assertThat(result.executionTimeMs()).isEqualTo(120);
            assertThat(result.memoryUsedKb()).isEqualTo(15360);
        }

        @Test
        @DisplayName("진행 중인 제출은 testResults가 비어있고 테스트 결과 조회를 하지 않는다")
        void returnsEmptyTestResultsWhenNotCompleted() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

            assertThat(result.status()).isEqualTo(SubmissionStatus.RUNNING);
            assertThat(result.testResults()).isEmpty();
            verify(submissionTestResultRepository, never()).findBySubmissionIdOrderByCreatedAtAscIdAsc(any());
        }

        @Test
        @DisplayName("실패한 제출은 failureCode를 반환하고 testResults 조회를 하지 않는다")
        void returnsFailureCodeWhenFailed() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markFailed(FailureCode.JUDGE0_RESPONSE_FAILURE);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

            assertThat(result.status()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(result.failureCode()).isEqualTo(FailureCode.JUDGE0_RESPONSE_FAILURE);
            verify(submissionTestResultRepository, never()).findBySubmissionIdOrderByCreatedAtAscIdAsc(any());
        }

        @Test
        @DisplayName("존재하지 않는 제출이면 SUBMISSION_NOT_FOUND 예외를 던진다")
        void throwsWhenSubmissionMissing() {
            UUID submissionId = UUID.randomUUID();
            given(submissionRepository.findById(submissionId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> submissionQueryService.getSubmission(submissionId, userId))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("다른 사용자의 제출이면 SUBMISSION_NOT_FOUND 예외를 던진다 (IDOR 방지 — 존재 여부와 구분되지 않음)")
        void throwsWhenNotOwnedByRequester() {
            UUID submissionId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionQueryService.getSubmission(submissionId, otherUserId))
                    .isInstanceOf(BusinessException.class);

            verify(submissionTestResultRepository, never()).findBySubmissionIdOrderByCreatedAtAscIdAsc(any());
        }

        @Test
        @DisplayName("비공개 테스트케이스는 실제 통과 여부와 별개로 actualOutput을 노출하지 않는다")
        void hidesActualOutputForNonPublicTestCase() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();
            submission.markCompleted(SubmissionResult.WRONG, 120, 15360);

            SubmissionTestResult hidden = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), false, false, null, null, null, null);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                    .willReturn(List.of(hidden));

            SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

            assertThat(result.testResults()).hasSize(1);
            assertThat(result.testResults().get(0).isPublic()).isFalse();
            assertThat(result.testResults().get(0).actualOutput()).isNull();
        }

        @Test
        @DisplayName("엔티티에 actualOutput이 남아있어도 isPublic=false면 응답 계층에서 한 번 더 null로 막는다")
        void nullsActualOutputEvenIfEntityHasStaleValue() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();
            submission.markCompleted(SubmissionResult.WRONG, 120, 15360);

            SubmissionTestResult hidden = SubmissionTestResult.create(
                    submissionId, UUID.randomUUID(), false, false, null, null, null, null);
            ReflectionTestUtils.setField(hidden, "actualOutput", "실제로는 절대 노출되면 안 되는 값");

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
            given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                    .willReturn(List.of(hidden));

            SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

            assertThat(result.testResults().get(0).actualOutput()).isNull();
        }

        @Test
        @DisplayName("Submission에 result/failureCode/executionTimeMs/memoryUsedKb 값이 남아있어도 RUNNING이면 응답에서는 null로 내려간다 (도메인 가드가 아니라 이 메서드 자체가 계약을 보장)")
        void nullsResultAndFailureCodeWhenNotTerminalEvenIfEntityHasStaleValue() {
            UUID submissionId = UUID.randomUUID();
            Submission submission = pendingSubmission(submissionId);
            submission.markQueued();
            submission.markRunning();
            ReflectionTestUtils.setField(submission, "result", SubmissionResult.CORRECT);
            ReflectionTestUtils.setField(submission, "failureCode", FailureCode.JUDGE0_RESPONSE_FAILURE);
            ReflectionTestUtils.setField(submission, "executionTimeMs", 999);
            ReflectionTestUtils.setField(submission, "memoryUsedKb", 99999);

            given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

            SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

            assertThat(result.status()).isEqualTo(SubmissionStatus.RUNNING);
            assertThat(result.result()).isNull();
            assertThat(result.failureCode()).isNull();
            assertThat(result.executionTimeMs()).isNull();
            assertThat(result.memoryUsedKb()).isNull();
            verify(submissionTestResultRepository, never()).findBySubmissionIdOrderByCreatedAtAscIdAsc(any());
        }
    }

    @Test
    @DisplayName("비공개 테스트케이스는 testCaseId도 노출하지 않는다 — UUID로 상관관계 추적 방지")
    void hidesTestCaseIdForNonPublicTestCase() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = pendingSubmission(submissionId);
        submission.markQueued();
        submission.markRunning();
        submission.markCompleted(SubmissionResult.WRONG, 120, 15360);

        SubmissionTestResult hidden = SubmissionTestResult.create(
                submissionId, UUID.randomUUID(), false, false, null, null, null, null);

        given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
        given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                .willReturn(List.of(hidden));

        SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

        assertThat(result.testResults()).hasSize(1);
        assertThat(result.testResults().get(0).testCaseId()).isNull();
        assertThat(result.testResults().get(0).isPublic()).isFalse();
        assertThat(result.testResults().get(0).passed()).isFalse();
    }

    @Test
    @DisplayName("공개 테스트케이스는 testCaseId를 그대로 노출한다")
    void exposesTestCaseIdForPublicTestCase() {
        UUID submissionId = UUID.randomUUID();
        UUID testCaseId = UUID.randomUUID();
        Submission submission = pendingSubmission(submissionId);
        submission.markQueued();
        submission.markRunning();
        submission.markCompleted(SubmissionResult.CORRECT, 120, 15360);

        SubmissionTestResult visible = SubmissionTestResult.create(
                submissionId, testCaseId, true, true, "8", null, null, null);

        given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));
        given(submissionTestResultRepository.findBySubmissionIdOrderByCreatedAtAscIdAsc(submissionId))
                .willReturn(List.of(visible));

        SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

        assertThat(result.testResults().get(0).testCaseId()).isEqualTo(testCaseId);
    }

    @Test
    @DisplayName("RETRY_WAIT 상태를 거친 제출도 진행 중으로 취급되어 testResults가 비어있다")
    void treatsRetryWaitAsInProgress() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = pendingSubmission(submissionId);
        submission.markQueued();
        submission.markRunning();
        submission.markRetryWait();

        given(submissionRepository.findById(submissionId)).willReturn(Optional.of(submission));

        SubmissionExternalGetResult result = submissionQueryService.getSubmission(submissionId, userId);

        assertThat(result.status()).isEqualTo(SubmissionStatus.RETRY_WAIT);
        assertThat(result.testResults()).isEmpty();
        assertThat(result.result()).isNull();
        assertThat(result.failureCode()).isNull();
        verify(submissionTestResultRepository, never()).findBySubmissionIdOrderByCreatedAtAscIdAsc(any());
    }
}