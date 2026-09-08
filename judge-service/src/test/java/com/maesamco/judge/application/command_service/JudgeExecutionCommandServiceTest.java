package com.maesamco.judge.application.command_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.maesamco.judge.application.port.JudgeExecutionPort;
import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionLanguage;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import com.maesamco.judge.domain.repository.ProblemExecutionSpecRepository;
import com.maesamco.judge.domain.repository.SubmissionRepository;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import com.maesamco.judge.infrastructure.messaging.event.ProblemPublishedEvent.TestCaseItem;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0Execution;
import com.maesamco.judge.infrastructure.persistence.PendingJudge0ExecutionRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JudgeExecutionCommandServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ProblemExecutionSpecRepository problemExecutionSpecRepository;

    @Mock
    private JudgeExecutionPort judgeExecutionPort;

    @Mock
    private PendingJudge0ExecutionRepository pendingJudge0ExecutionRepository;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @InjectMocks
    private JudgeExecutionCommandService judgeExecutionCommandService;

    private Submission queuedSubmission() {
        Submission submission = Submission.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "public class Main {}", SubmissionLanguage.JAVA17, "idem-key-" + UUID.randomUUID());
        submission.markQueued();
        return submission;
    }

    private ProblemExecutionSpec specFor(Submission submission, String testCasesJson) {
        return ProblemExecutionSpec.fromPublishedEvent(
                submission.getProblemId(), submission.getProblemVersionId(),
                SubmissionLanguage.JAVA17, "starter", testCasesJson, 2000, 256, Instant.now());
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("정상 흐름에서는 토큰별로 PendingJudge0Execution을 저장하고 RUNNING으로 전이한다")
        void savesTokensAndMarksRunning() {
            // given
            Submission submission = queuedSubmission();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new TestCaseItem(UUID.randomUUID(), true, "3 5", "8", 1),
                    new TestCaseItem(UUID.randomUUID(), false, "1 1", "2", 2)
            ));
            ProblemExecutionSpec spec = specFor(submission, testCasesJson);

            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));
            given(problemExecutionSpecRepository.findByProblemIdAndProblemVersionId(
                    submission.getProblemId(), submission.getProblemVersionId())).willReturn(Optional.of(spec));
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(List.of("token-1", "token-2"));            // when
            judgeExecutionCommandService.execute(submission.getId());

            // then
            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);

            ArgumentCaptor<List<PendingJudge0Execution>> captor = ArgumentCaptor.forClass(List.class);
            verify(pendingJudge0ExecutionRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
        }

        @Test
        @DisplayName("존재하지 않는 제출이면 SUBMISSION_NOT_FOUND 예외를 던진다")
        void throwsWhenSubmissionNotFound() {
            // given
            UUID submissionId = UUID.randomUUID();
            given(submissionRepository.findById(submissionId)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> judgeExecutionCommandService.execute(submissionId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBMISSION_NOT_FOUND);

            verify(judgeExecutionPort, never()).submitBatch(any());
        }

        @Test
        @DisplayName("실행 명세가 없으면 PROBLEM_NOT_FOUND 예외를 던진다")
        void throwsWhenProblemExecutionSpecNotFound() {
            // given
            Submission submission = queuedSubmission();
            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));
            given(problemExecutionSpecRepository.findByProblemIdAndProblemVersionId(
                    submission.getProblemId(), submission.getProblemVersionId())).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> judgeExecutionCommandService.execute(submission.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROBLEM_NOT_FOUND);

            verify(judgeExecutionPort, never()).submitBatch(any());
        }

        @Test
        @DisplayName("토큰이 null인 테스트케이스는 저장하지 않고 건너뛴다")
        void skipsNullTokens() {
            // given
            Submission submission = queuedSubmission();
            String testCasesJson = jsonMapper.writeValueAsString(List.of(
                    new TestCaseItem(UUID.randomUUID(), true, "3 5", "8", 1),
                    new TestCaseItem(UUID.randomUUID(), true, "1 1", "2", 2)
            ));
            ProblemExecutionSpec spec = specFor(submission, testCasesJson);

            given(submissionRepository.findById(submission.getId())).willReturn(Optional.of(submission));
            given(problemExecutionSpecRepository.findByProblemIdAndProblemVersionId(
                    submission.getProblemId(), submission.getProblemVersionId())).willReturn(Optional.of(spec));
            given(judgeExecutionPort.submitBatch(anyList())).willReturn(Arrays.asList("token-1", null));
            // when
            judgeExecutionCommandService.execute(submission.getId());

            // then
            ArgumentCaptor<List<PendingJudge0Execution>> captor = ArgumentCaptor.forClass(List.class);
            verify(pendingJudge0ExecutionRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
        }
    }
}