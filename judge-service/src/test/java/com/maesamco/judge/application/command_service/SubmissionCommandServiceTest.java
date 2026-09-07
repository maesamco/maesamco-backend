package com.maesamco.judge.application.command_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SubmissionCommandServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ProblemExecutionSpecRepository problemExecutionSpecRepository;

    @Mock
    private SubmissionSaveExecutor submissionSaveExecutor;

    @InjectMocks
    private SubmissionCommandService submissionCommandService;

    private final UUID userId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();
    private final UUID problemVersionId = UUID.randomUUID();
    private final String idempotencyKey = "idem-key-1";

    private SubmissionCreateCommand command(String code, String language) {
        return new SubmissionCreateCommand(userId, idempotencyKey, problemId, code, language);
    }

    private ProblemExecutionSpec spec() {
        ProblemExecutionSpec spec = mock(ProblemExecutionSpec.class);
        given(spec.getProblemVersionId()).willReturn(problemVersionId);
        given(spec.getLanguage()).willReturn(SubmissionLanguage.JAVA17);
        return spec;
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("정상 제출이면 PENDING으로 저장하고 Outbox를 기록한 뒤 결과를 반환한다")
        void savesAndPublishesOutboxOnSuccess() {
            // given
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
            ProblemExecutionSpec spec = spec();
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.of(spec));
            given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId)).willReturn(0);

            // when
            SubmissionCreateResult result = submissionCommandService.submit(command("public class Main {}", "JAVA17"));

            // then
            verify(submissionSaveExecutor, times(1)).saveWithOutbox(any(Submission.class));
            assertThat(result.status()).isEqualTo(SubmissionStatus.PENDING);
        }

        @Test
        @DisplayName("문제 실행 명세가 없으면 PROBLEM_NOT_FOUND를 던진다")
        void throwsWhenSpecNotFound() {
            // given
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> submissionCommandService.submit(command("code", "JAVA17")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROBLEM_NOT_FOUND);
            verify(submissionSaveExecutor, never()).saveWithOutbox(any());
        }

        @Test
        @DisplayName("같은 Idempotency-Key + 같은 바디로 재요청하면 기존 결과를 그대로 반환한다")
        void returnsExistingResultWhenSameBodyRetried() {
            // given
            Submission existing = Submission.create(
                    userId, problemId, problemVersionId, 1,
                    "public class Main {}", SubmissionLanguage.JAVA17, idempotencyKey
            );
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.of(existing));

            // when
            SubmissionCreateResult result = submissionCommandService.submit(command(existing.getCode(), "JAVA17"));

            // then
            assertThat(result.submissionId()).isEqualTo(existing.getId());
            verify(submissionSaveExecutor, never()).saveWithOutbox(any());
        }

        @Test
        @DisplayName("같은 Idempotency-Key + 다른 바디로 재요청하면 IDEMPOTENCY_KEY_CONFLICT를 던진다")
        void throwsConflictWhenDifferentBodySameKey() {
            // given
            Submission existing = Submission.create(
                    userId, problemId, problemVersionId, 1,
                    "public class Main {}", SubmissionLanguage.JAVA17, idempotencyKey
            );
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.of(existing));

            // when / then
            assertThatThrownBy(() -> submissionCommandService.submit(command("다른 코드", "JAVA17")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        @Test
        @DisplayName("같은 Idempotency-Key + 같은 바디라도 다른 사용자가 보낸 요청이면 IDEMPOTENCY_KEY_CONFLICT를 던진다")
        void throwsConflictWhenDifferentUserSameKeyAndBody() {
            // given
            UUID otherUserId = UUID.randomUUID();
            Submission existing = Submission.create(
                    otherUserId, problemId, problemVersionId, 1,
                    "public class Main {}", SubmissionLanguage.JAVA17, idempotencyKey
            );
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.of(existing));

            // when / then — command는 userId(본인) 필드를 쓰고, existing은 otherUserId 소유
            assertThatThrownBy(() -> submissionCommandService.submit(command(existing.getCode(), "JAVA17")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        @Test
        @DisplayName("attemptNo 경합(DataIntegrityViolationException)이 나도 재조회 후 재시도해 성공한다")
        void retriesOnAttemptNoRaceAndSucceeds() {
            // given
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
            ProblemExecutionSpec spec = spec();
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.of(spec));
            given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId))
                    .willReturn(0, 1);
            given(submissionRepository.findByIdempotencyKey(idempotencyKey))
                    .willReturn(Optional.empty());

            // saveWithOutbox는 void라 doThrow().doNothing() 그대로 사용 가능
            doThrow(new DataIntegrityViolationException("unique violation"))
                    .doNothing()
                    .when(submissionSaveExecutor).saveWithOutbox(any(Submission.class));

            // when
            SubmissionCreateResult result = submissionCommandService.submit(command("code", "JAVA17"));

            // then
            verify(submissionSaveExecutor, times(2)).saveWithOutbox(any(Submission.class));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("최대 재시도 횟수를 다 소진하면 INTERNAL_SERVER_ERROR를 던진다")
        void throwsInternalErrorWhenRetryExhausted() {
            // given
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
            ProblemExecutionSpec spec = spec();
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.of(spec));
            given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId)).willReturn(0);
            doThrow(new DataIntegrityViolationException("unique violation"))
                    .when(submissionSaveExecutor).saveWithOutbox(any(Submission.class));

            // when / then
            assertThatThrownBy(() -> submissionCommandService.submit(command("code", "JAVA17")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR);
            verify(submissionSaveExecutor, times(3)).saveWithOutbox(any());
        }

        @Test
        @DisplayName("language가 SubmissionLanguage에 없는 값이면 INVALID_INPUT_VALUE를 던진다")
        void throwsWhenLanguageInvalid() {
            // given
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.of(mock(ProblemExecutionSpec.class)));

            // when / then
            assertThatThrownBy(() -> submissionCommandService.submit(command("code", "PYTH0N")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}