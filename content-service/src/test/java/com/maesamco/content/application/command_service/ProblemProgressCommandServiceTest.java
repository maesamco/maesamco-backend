package com.maesamco.content.application.command_service;

import com.maesamco.content.application.command.ProblemProgressSyncCommand;
import com.maesamco.content.application.finder.ProblemProgressFinder;
import com.maesamco.content.application.finder.ProblemVersionFinder;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemProgressCommandServiceTest {

    @Mock
    private ProblemProgressFinder problemProgressFinder;

    @Mock
    private ProblemVersionFinder problemVersionFinder;

    @Mock
    private ProblemProgressRepository problemProgressRepository;

    @Mock
    private ProblemVersion problemVersion;

    private ProblemProgressCommandService problemProgressCommandService;

    private UUID submissionId;
    private UUID userId;
    private UUID problemId;
    private UUID problemVersionId;

    @BeforeEach
    void setUp() {
        problemProgressCommandService = new ProblemProgressCommandService(
                problemProgressFinder,
                problemVersionFinder,
                problemProgressRepository
        );

        submissionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        problemId = UUID.randomUUID();
        problemVersionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("채점 상태가 COMPLETED가 아니면 ProblemProgress를 동기화하지 않는다")
    void sync_notCompleted_ignoresEvent() {
        // given
        ProblemProgressSyncCommand command = createCommand(
                1,
                "JUDGING",
                "CORRECT",
                Instant.parse("2026-09-21T00:00:00Z")
        );

        // when
        problemProgressCommandService.sync(command);

        // then
        verifyNoInteractions(
                problemProgressFinder,
                problemVersionFinder,
                problemProgressRepository
        );
    }

    @Test
    @DisplayName("최초 채점 결과가 CORRECT이면 정답 상태의 ProblemProgress를 생성한다")
    void sync_firstCorrect_createsProgress() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        ProblemProgressSyncCommand command = createCommand(
                1,
                "COMPLETED",
                "CORRECT",
                judgedAt
        );

        when(problemProgressFinder.getByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.empty());

        when(problemVersionFinder.getById(problemVersionId))
                .thenReturn(problemVersion);

        when(problemVersion.getVersionNo())
                .thenReturn(3);

        // when
        problemProgressCommandService.sync(command);

        // then
        ArgumentCaptor<ProblemProgress> progressCaptor = ArgumentCaptor.forClass(ProblemProgress.class);

        verify(problemProgressRepository).save(progressCaptor.capture());

        ProblemProgress savedProgress = progressCaptor.getValue();

        assertThat(savedProgress.getUserId()).isEqualTo(userId);
        assertThat(savedProgress.getProblemId()).isEqualTo(problemId);
        assertThat(savedProgress.getVersionNo()).isEqualTo(3);
        assertThat(savedProgress.getAttemptNo()).isEqualTo(1);
        assertThat(savedProgress.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
        assertThat(savedProgress.getCreatedAt()).isEqualTo(judgedAt);
        assertThat(savedProgress.getSolvedAt()).isEqualTo(judgedAt);
    }

    @Test
    @DisplayName("최초 채점 결과가 WRONG이면 오답 상태의 ProblemProgress를 생성한다")
    void sync_firstWrong_createsWrongProgress() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        ProblemProgressSyncCommand command = createCommand(
                1,
                "COMPLETED",
                "WRONG",
                judgedAt
        );

        when(problemProgressFinder.getByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.empty());

        when(problemVersionFinder.getById(problemVersionId))
                .thenReturn(problemVersion);

        when(problemVersion.getVersionNo())
                .thenReturn(1);

        // when
        problemProgressCommandService.sync(command);

        // then
        ArgumentCaptor<ProblemProgress> progressCaptor = ArgumentCaptor.forClass(ProblemProgress.class);

        verify(problemProgressRepository).save(progressCaptor.capture());

        ProblemProgress savedProgress = progressCaptor.getValue();

        assertThat(savedProgress.getUserId()).isEqualTo(userId);
        assertThat(savedProgress.getProblemId()).isEqualTo(problemId);
        assertThat(savedProgress.getVersionNo()).isEqualTo(1);
        assertThat(savedProgress.getAttemptNo()).isEqualTo(1);
        assertThat(savedProgress.getProgressStatus()).isEqualTo(ProblemProgressStatus.WRONG);
        assertThat(savedProgress.getCreatedAt()).isEqualTo(judgedAt);
        assertThat(savedProgress.getSolvedAt()).isNull();
    }

    @Test
    @DisplayName("더 최신 제출 결과가 들어오면 버전, 시도 번호, 상태를 갱신한다")
    void sync_newerAttempt_updatesProgress() {
        // given
        Instant firstJudgedAt = Instant.parse("2026-09-21T00:00:00Z");
        Instant secondJudgedAt = Instant.parse("2026-09-21T01:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                firstJudgedAt
        );

        ProblemProgressSyncCommand command = createCommand(
                2,
                "COMPLETED",
                "CORRECT",
                secondJudgedAt
        );

        when(problemProgressFinder.getByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.of(progress));

        when(problemVersionFinder.getById(problemVersionId))
                .thenReturn(problemVersion);

        when(problemVersion.getVersionNo())
                .thenReturn(2);

        // when
        problemProgressCommandService.sync(command);

        // then
        assertThat(progress.getVersionNo()).isEqualTo(2);
        assertThat(progress.getAttemptNo()).isEqualTo(2);
        assertThat(progress.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
        assertThat(progress.getCreatedAt()).isEqualTo(firstJudgedAt);
        assertThat(progress.getSolvedAt()).isEqualTo(secondJudgedAt);

        verifyNoInteractions(problemProgressRepository);
    }

    @Test
    @DisplayName("이전 제출 결과가 늦게 도착하면 최신 버전, 시도 번호, 상태를 덮어쓰지 않는다")
    void sync_olderAttempt_doesNotOverwriteLatestState() {
        // given
        Instant latestJudgedAt = Instant.parse("2026-09-21T02:00:00Z");
        Instant olderJudgedAt = Instant.parse("2026-09-21T01:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                3,
                3,
                ProblemProgressStatus.CORRECT,
                latestJudgedAt
        );

        ProblemProgressSyncCommand command = createCommand(
                2,
                "COMPLETED",
                "WRONG",
                olderJudgedAt
        );

        when(problemProgressFinder.getByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.of(progress));

        // when
        problemProgressCommandService.sync(command);

        // then
        assertThat(progress.getVersionNo()).isEqualTo(3);
        assertThat(progress.getAttemptNo()).isEqualTo(3);
        assertThat(progress.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);

        verify(problemProgressRepository).save(progress);
        verifyNoInteractions(problemVersionFinder);
    }

    @Test
    @DisplayName("이전 제출 이벤트가 늦게 도착해도 더 이른 최초 채점 시각은 반영한다")
    void sync_olderAttempt_updatesEarlierCreatedAt() {
        // given
        Instant currentCreatedAt = Instant.parse("2026-09-21T02:00:00Z");
        Instant earlierJudgedAt = Instant.parse("2026-09-21T01:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                3,
                3,
                ProblemProgressStatus.WRONG,
                currentCreatedAt
        );

        ProblemProgressSyncCommand command = createCommand(
                2,
                "COMPLETED",
                "WRONG",
                earlierJudgedAt
        );

        when(problemProgressFinder.getByUserIdAndProblemId(userId, problemId))
                .thenReturn(Optional.of(progress));

        // when
        problemProgressCommandService.sync(command);

        // then
        assertThat(progress.getCreatedAt()).isEqualTo(earlierJudgedAt);
        assertThat(progress.getVersionNo()).isEqualTo(3);
        assertThat(progress.getAttemptNo()).isEqualTo(3);
        assertThat(progress.getProgressStatus()).isEqualTo(ProblemProgressStatus.WRONG);

        verify(problemProgressRepository).save(progress);
        verifyNoInteractions(problemVersionFinder);
    }

    @Test
    @DisplayName("지원하지 않는 채점 결과이면 예외가 발생한다")
    void sync_invalidResult_throwsException() {
        // given
        ProblemProgressSyncCommand command = createCommand(
                1,
                "COMPLETED",
                "UNKNOWN_RESULT",
                Instant.parse("2026-09-21T00:00:00Z")
        );

        // when & then
        assertThatThrownBy(() -> problemProgressCommandService.sync(command))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PROBLEM_PROGRESS_INVALID_SUBMISSION_RESULT);

        verifyNoInteractions(
                problemProgressFinder,
                problemVersionFinder,
                problemProgressRepository
        );
    }

    private ProblemProgressSyncCommand createCommand(
            int attemptNo,
            String status,
            String result,
            Instant judgedAt
    ) {
        return new ProblemProgressSyncCommand(
                submissionId,
                userId,
                problemId,
                problemVersionId,
                attemptNo,
                status,
                result,
                judgedAt
        );
    }
}