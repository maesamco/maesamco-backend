package com.maesamco.judge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maesamco.judge.global.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Submission 상태 전이 테스트")
class SubmissionTest {

    private Submission pendingSubmission;

    @BeforeEach
    void setUp() {
        pendingSubmission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA,
                UUID.randomUUID().toString()
        );
    }

    @Nested
    @DisplayName("정상 상태 전이 테스트")
    class ValidTransitionTest {

        @Test
        @DisplayName("전이 성공 - PENDING에서 QUEUED로")
        void markQueued_success_fromPending() {
            // when
            pendingSubmission.markQueued();

            // then
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.QUEUED);
        }

        @Test
        @DisplayName("전이 성공 - QUEUED에서 RUNNING으로")
        void markRunning_success_fromQueued() {
            // given
            pendingSubmission.markQueued();

            // when
            pendingSubmission.markRunning();

            // then
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
        }

        @Test
        @DisplayName("전이 성공 - RETRY_WAIT에서 RUNNING으로 재진입")
        void markRunning_success_fromRetryWait() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();
            pendingSubmission.markRetryWait();

            // when
            pendingSubmission.markRunning();

            // then
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
        }

        @Test
        @DisplayName("전이 성공 - RUNNING에서 RETRY_WAIT로, retryCount 증가")
        void markRetryWait_success_fromRunning() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();

            // when
            pendingSubmission.markRetryWait();

            // then
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.RETRY_WAIT);
            assertThat(pendingSubmission.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("전이 성공 - RUNNING에서 COMPLETED로, result 채워지고 failureCode는 비워짐")
        void markCompleted_success_fromRunning() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();

            // when
            pendingSubmission.markCompleted(SubmissionResult.CORRECT, 100, 2048);

            // then
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.COMPLETED);
            assertThat(pendingSubmission.getResult()).isEqualTo(SubmissionResult.CORRECT);
            assertThat(pendingSubmission.getFailureCode()).isNull();
            assertThat(pendingSubmission.getExecutionTimeMs()).isEqualTo(100);
            assertThat(pendingSubmission.getMemoryUsedKb()).isEqualTo(2048);
            assertThat(pendingSubmission.getJudgedAt()).isNotNull();
            assertThat(pendingSubmission.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("전이 성공 - RUNNING에서 FAILED로, failureCode 채워지고 result는 비워짐")
        void markFailed_success_fromRunning() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();

            // when
            pendingSubmission.markFailed(FailureCode.INTERNAL_SYSTEM_ERROR);

            // then
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
            assertThat(pendingSubmission.getFailureCode()).isEqualTo(FailureCode.INTERNAL_SYSTEM_ERROR);
            assertThat(pendingSubmission.getResult()).isNull();
            assertThat(pendingSubmission.isTerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("금지된 상태 전이 테스트")
    class InvalidTransitionTest {

        @Test
        @DisplayName("전이 실패 - PENDING에서 바로 RUNNING으로 갈 수 없음")
        void markRunning_fail_fromPending() {
            // when & then
            assertThatThrownBy(pendingSubmission::markRunning)
                    .isInstanceOf(BusinessException.class);
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        }

        @Test
        @DisplayName("전이 실패 - QUEUED에서 바로 RETRY_WAIT로 갈 수 없음")
        void markRetryWait_fail_fromQueued() {
            // given
            pendingSubmission.markQueued();

            // when & then
            assertThatThrownBy(pendingSubmission::markRetryWait)
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("전이 실패 - 터미널 상태(COMPLETED)에서 지연된 RUNNING 이벤트가 와도 역행하지 않음")
        void markRunning_fail_fromCompleted_terminalGuard() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();
            pendingSubmission.markCompleted(SubmissionResult.CORRECT, 100, 1024);

            // when & then
            assertThatThrownBy(pendingSubmission::markRunning)
                    .isInstanceOf(BusinessException.class);
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.COMPLETED);
        }

        @Test
        @DisplayName("전이 실패 - 터미널 상태(FAILED)에서 RETRY_WAIT로 되돌아갈 수 없음")
        void markRetryWait_fail_fromFailed_terminalGuard() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();
            pendingSubmission.markFailed(FailureCode.JUDGE0_RESPONSE_FAILURE);

            // when & then
            assertThatThrownBy(pendingSubmission::markRetryWait)
                    .isInstanceOf(BusinessException.class);
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        }

        @Test
        @DisplayName("전이 실패 - COMPLETED에서 FAILED로 전이할 수 없고 기존 값도 유지됨")
        void markFailed_fail_fromCompleted_terminalGuard() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();
            pendingSubmission.markCompleted(SubmissionResult.CORRECT, 100, 1024);

            // when & then
            assertThatThrownBy(() -> pendingSubmission.markFailed(FailureCode.RESULT_SAVE_FAILURE))
                    .isInstanceOf(BusinessException.class);
            assertThat(pendingSubmission.getResult()).isEqualTo(SubmissionResult.CORRECT);
        }
    }

    @Nested
    @DisplayName("중복·지연 이벤트 멱등 처리 테스트")
    class IdempotentDuplicateEventTest {

        @Test
        @DisplayName("멱등 처리 - 동일 RUNNING 이벤트 중복 도착 시 예외 없이 무시됨")
        void markRunning_idempotent_duplicateEvent() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();

            // when & then
            assertThatCode(pendingSubmission::markRunning).doesNotThrowAnyException();
            assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
        }

        @Test
        @DisplayName("멱등 처리 - 동일 COMPLETED 이벤트 중복 도착 시 기존 값이 유지됨")
        void markCompleted_idempotent_duplicateEvent() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();
            pendingSubmission.markCompleted(SubmissionResult.CORRECT, 100, 1024);
            SubmissionResult originalResult = pendingSubmission.getResult();

            // when
            pendingSubmission.markCompleted(SubmissionResult.WRONG, 999, 999);

            // then
            assertThat(pendingSubmission.getResult()).isEqualTo(originalResult);
        }

        @Test
        @DisplayName("멱등 처리 - 동일 FAILED 이벤트 중복 도착 시 기존 값이 유지됨")
        void markFailed_idempotent_duplicateEvent() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();
            pendingSubmission.markFailed(FailureCode.JUDGE0_RESPONSE_FAILURE);
            FailureCode originalFailureCode = pendingSubmission.getFailureCode();

            // when
            pendingSubmission.markFailed(FailureCode.KAFKA_PROCESSING_FAILURE);

            // then
            assertThat(pendingSubmission.getFailureCode()).isEqualTo(originalFailureCode);
        }

        @Test
        @DisplayName("멱등 처리 - 동일 RETRY_WAIT 이벤트 중복 도착 시 retryCount가 또 증가하지 않음")
        void markRetryWait_idempotent_duplicateEvent() {
            // given
            pendingSubmission.markQueued();
            pendingSubmission.markRunning();
            pendingSubmission.markRetryWait();
            int originalRetryCount = pendingSubmission.getRetryCount();

            // when
            pendingSubmission.markRetryWait();

            // then
            assertThat(pendingSubmission.getRetryCount()).isEqualTo(originalRetryCount);
        }
    }

    @Nested
    @DisplayName("생성 시 도메인 불변식 검증 테스트")
    class InvariantValidationTest {

        @Test
        @DisplayName("생성 실패 - userId가 null이면 예외")
        void create_fail_nullUserId() {
            assertThatThrownBy(() -> Submission.create(
                    null, UUID.randomUUID(), UUID.randomUUID(),
                    1, "code", SubmissionLanguage.JAVA, UUID.randomUUID().toString()
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("생성 실패 - attemptNo가 0 이하면 예외")
        void create_fail_nonPositiveAttemptNo() {
            assertThatThrownBy(() -> Submission.create(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    0, "code", SubmissionLanguage.JAVA, UUID.randomUUID().toString()
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("생성 실패 - code가 100KB(UTF-8 바이트 기준)를 초과하면 예외")
        void create_fail_codeExceeds100KB() {
            String oversizedCode = "a".repeat(101 * 1024);

            assertThatThrownBy(() -> Submission.create(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    1, oversizedCode, SubmissionLanguage.JAVA, UUID.randomUUID().toString()
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("생성 실패 - idempotencyKey가 공백이면 예외")
        void create_fail_blankIdempotencyKey() {
            assertThatThrownBy(() -> Submission.create(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    1, "code", SubmissionLanguage.JAVA, "   "
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }
}