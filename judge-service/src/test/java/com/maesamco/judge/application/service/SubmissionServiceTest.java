package com.maesamco.judge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.maesamco.judge.application.SubmissionService;
import com.maesamco.judge.application.command.SubmissionCreateCommand;
import com.maesamco.judge.application.result.SubmissionCreateResult;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionEventOutboxRepository;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionEventOutboxRepository submissionEventOutboxRepository;

    @Mock
    private ProblemExecutionSpecRepository problemExecutionSpecRepository;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @InjectMocks
    private SubmissionService submissionService;

    private final UUID userId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();
    private final UUID problemVersionId = UUID.randomUUID();
    private final String idempotencyKey = "idem-key-1";

    private SubmissionCreateCommand command(String code, String language) {
        return new SubmissionCreateCommand(userId, idempotencyKey, problemId, code, language);
    }

    private ProblemExecutionSpec spec() {
        ProblemExecutionSpec spec = mock(ProblemExecutionSpec.class); // 실제 프로젝트 엔티티 생성 방식에 맞게 교체 필요
        given(spec.getProblemVersionId()).willReturn(problemVersionId);
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
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.of(spec()));
            given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId)).willReturn(0);

            // when
            SubmissionCreateResult result = submissionService.submit(command("public class Main {}", "JAVA"));

            // then
            verify(submissionRepository, times(1)).saveAndFlush(any(Submission.class));
            verify(submissionEventOutboxRepository, times(1)).save(any());
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
            assertThatThrownBy(() -> submissionService.submit(command("code", "JAVA")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROBLEM_NOT_FOUND);
            verify(submissionRepository, never()).saveAndFlush(any());
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
            SubmissionCreateResult result = submissionService.submit(command(existing.getCode(), "JAVA"));

            // then
            assertThat(result.submissionId()).isEqualTo(existing.getId());
            verify(submissionRepository, never()).saveAndFlush(any());
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
            assertThatThrownBy(() -> submissionService.submit(command("다른 코드", "JAVA")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        @Test
        @DisplayName("attemptNo 경합(DataIntegrityViolationException)이 나도 재조회 후 재시도해 성공한다")
        void retriesOnAttemptNoRaceAndSucceeds() {
            // given
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.of(spec()));
            given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId))
                    .willReturn(0, 1); // 1차 계산 0, 재시도 시 1로 다시 계산됐다고 가정
            given(submissionRepository.findByIdempotencyKey(idempotencyKey))
                    .willReturn(Optional.empty()); // 재조회에서도 자기 키로는 아무것도 안 나옴(케이스 2)

            // 1차 저장 실패 → 2차 저장 성공
            org.mockito.Mockito.doThrow(new DataIntegrityViolationException("unique violation"))
                    .doNothing()
                    .when(submissionRepository).saveAndFlush(any(Submission.class));

            // when
            SubmissionCreateResult result = submissionService.submit(command("code", "JAVA"));

            // then
            verify(submissionRepository, times(2)).saveAndFlush(any(Submission.class));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("최대 재시도 횟수를 다 소진하면 INTERNAL_SERVER_ERROR를 던진다")
        void throwsInternalErrorWhenRetryExhausted() {
            // given
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.of(spec()));
            given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId)).willReturn(0);
            org.mockito.Mockito.doThrow(new DataIntegrityViolationException("unique violation"))
                    .when(submissionRepository).saveAndFlush(any(Submission.class));

            // when / then
            assertThatThrownBy(() -> submissionService.submit(command("code", "JAVA")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR);
            verify(submissionRepository, times(3)).saveAndFlush(any()); // MAX_SAVE_RETRY=3
        }

        @Test
        @DisplayName("language가 SubmissionLanguage에 없는 값이면 INVALID_INPUT_VALUE를 던진다")
        void throwsWhenLanguageInvalid() {
            // given
            given(submissionRepository.findByIdempotencyKey(idempotencyKey)).willReturn(Optional.empty());
            given(problemExecutionSpecRepository.findFirstByProblemIdOrderByPublishedAtDesc(problemId))
                    .willReturn(Optional.of(spec()));
            given(submissionRepository.findMaxAttemptNoByUserIdAndProblemId(userId, problemId)).willReturn(0);

            // when / then
            assertThatThrownBy(() -> submissionService.submit(command("code", "PYTH0N")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}