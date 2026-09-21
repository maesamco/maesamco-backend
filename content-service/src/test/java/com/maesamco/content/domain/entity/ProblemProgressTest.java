package com.maesamco.content.domain.entity;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemProgressTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();

    @Test
    @DisplayName("최초 채점 결과가 WRONG이면 ProblemProgress를 오답 상태로 생성한다")
    void create_wrong_createsWrongProgress() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        // when
        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                judgedAt
        );

        // then
        assertThat(progress.getId()).isNotNull();
        assertThat(progress.getUserId()).isEqualTo(userId);
        assertThat(progress.getProblemId()).isEqualTo(problemId);
        assertThat(progress.getVersionNo()).isEqualTo(1);
        assertThat(progress.getAttemptNo()).isEqualTo(1);
        assertThat(progress.getProgressStatus()).isEqualTo(ProblemProgressStatus.WRONG);
        assertThat(progress.getCreatedAt()).isEqualTo(judgedAt);
        assertThat(progress.getSolvedAt()).isNull();
    }

    @Test
    @DisplayName("최초 채점 결과가 CORRECT이면 judgedAt을 solvedAt으로 저장한다")
    void create_correct_setsSolvedAt() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        // when
        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.CORRECT,
                judgedAt
        );

        // then
        assertThat(progress.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
        assertThat(progress.getCreatedAt()).isEqualTo(judgedAt);
        assertThat(progress.getSolvedAt()).isEqualTo(judgedAt);
    }

    @Test
    @DisplayName("userId가 null이면 ProblemProgress를 생성할 수 없다")
    void create_nullUserId_throwsException() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        // when & then
        assertBusinessException(
                () -> ProblemProgress.create(
                        null,
                        problemId,
                        1,
                        1,
                        ProblemProgressStatus.WRONG,
                        judgedAt
                ),
                ErrorCode.PROBLEM_PROGRESS_INVALID_USER_ID
        );
    }

    @Test
    @DisplayName("problemId가 null이면 ProblemProgress를 생성할 수 없다")
    void create_nullProblemId_throwsException() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        // when & then
        assertBusinessException(
                () -> ProblemProgress.create(
                        userId,
                        null,
                        1,
                        1,
                        ProblemProgressStatus.WRONG,
                        judgedAt
                ),
                ErrorCode.PROBLEM_PROGRESS_INVALID_PROBLEM_ID
        );
    }

    @Test
    @DisplayName("진행 상태가 null이면 ProblemProgress를 생성할 수 없다")
    void create_nullStatus_throwsException() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        // when & then
        assertBusinessException(
                () -> ProblemProgress.create(
                        userId,
                        problemId,
                        1,
                        1,
                        null,
                        judgedAt
                ),
                ErrorCode.PROBLEM_PROGRESS_INVALID_STATUS
        );
    }

    @Test
    @DisplayName("judgedAt이 null이면 ProblemProgress를 생성할 수 없다")
    void create_nullJudgedAt_throwsException() {
        // when & then
        assertBusinessException(
                () -> ProblemProgress.create(
                        userId,
                        problemId,
                        1,
                        1,
                        ProblemProgressStatus.WRONG,
                        null
                ),
                ErrorCode.PROBLEM_PROGRESS_INVALID_JUDGED_AT
        );
    }

    @Test
    @DisplayName("versionNo가 1보다 작으면 ProblemProgress를 생성할 수 없다")
    void create_invalidVersionNo_throwsException() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        // when & then
        assertBusinessException(
                () -> ProblemProgress.create(
                        userId,
                        problemId,
                        0,
                        1,
                        ProblemProgressStatus.WRONG,
                        judgedAt
                ),
                ErrorCode.PROBLEM_VERSION_INVALID_VERSION_NO
        );
    }

    @Test
    @DisplayName("attemptNo가 1보다 작으면 ProblemProgress를 생성할 수 없다")
    void create_invalidAttemptNo_throwsException() {
        // given
        Instant judgedAt = Instant.parse("2026-09-21T00:00:00Z");

        // when & then
        assertBusinessException(
                () -> ProblemProgress.create(
                        userId,
                        problemId,
                        1,
                        0,
                        ProblemProgressStatus.WRONG,
                        judgedAt
                ),
                ErrorCode.PROBLEM_PROGRESS_INVALID_ATTEMPT_NO
        );
    }

    @Test
    @DisplayName("문제 버전 번호를 변경한다")
    void changeVersionNo_changesVersionNo() {
        // given
        ProblemProgress progress = createProgress(1, ProblemProgressStatus.WRONG);

        // when
        progress.changeVersionNo(2);

        // then
        assertThat(progress.getVersionNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("현재보다 큰 attemptNo이면 마지막 제출 시도 번호를 변경한다")
    void changeAttemptNo_newerAttempt_changesAttemptNo() {
        // given
        ProblemProgress progress = createProgress(1, ProblemProgressStatus.WRONG);

        // when
        progress.changeAttemptNo(2);

        // then
        assertThat(progress.getAttemptNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("현재와 같거나 이전 attemptNo이면 마지막 제출 시도 번호를 변경하지 않는다")
    void changeAttemptNo_oldOrSameAttempt_keepsAttemptNo() {
        // given
        ProblemProgress progress = createProgress(3, ProblemProgressStatus.WRONG);

        // when
        progress.changeAttemptNo(3);
        progress.changeAttemptNo(2);

        // then
        assertThat(progress.getAttemptNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("CORRECT 결과가 처음 들어오면 judgedAt을 solvedAt으로 저장한다")
    void changeSolvedAt_firstCorrect_setsSolvedAt() {
        // given
        ProblemProgress progress = createProgress(1, ProblemProgressStatus.WRONG);
        Instant judgedAt = Instant.parse("2026-09-21T01:00:00Z");

        // when
        progress.changeSolvedAt(ProblemProgressStatus.CORRECT, judgedAt);

        // then
        assertThat(progress.getSolvedAt()).isEqualTo(judgedAt);
    }

    @Test
    @DisplayName("더 이른 CORRECT 결과가 늦게 도착하면 solvedAt을 더 이른 시각으로 보정한다")
    void changeSolvedAt_earlierCorrect_changesSolvedAt() {
        // given
        Instant firstReceivedAt = Instant.parse("2026-09-21T02:00:00Z");
        Instant earlierJudgedAt = Instant.parse("2026-09-21T01:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                2,
                ProblemProgressStatus.CORRECT,
                firstReceivedAt
        );

        // when
        progress.changeSolvedAt(ProblemProgressStatus.CORRECT, earlierJudgedAt);

        // then
        assertThat(progress.getSolvedAt()).isEqualTo(earlierJudgedAt);
    }

    @Test
    @DisplayName("기존 solvedAt보다 늦은 CORRECT 결과는 최초 정답 시각을 변경하지 않는다")
    void changeSolvedAt_laterCorrect_keepsSolvedAt() {
        // given
        Instant firstCorrectAt = Instant.parse("2026-09-21T01:00:00Z");
        Instant laterCorrectAt = Instant.parse("2026-09-21T02:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.CORRECT,
                firstCorrectAt
        );

        // when
        progress.changeSolvedAt(ProblemProgressStatus.CORRECT, laterCorrectAt);

        // then
        assertThat(progress.getSolvedAt()).isEqualTo(firstCorrectAt);
    }

    @Test
    @DisplayName("WRONG 결과는 solvedAt을 변경하지 않는다")
    void changeSolvedAt_wrong_keepsSolvedAt() {
        // given
        ProblemProgress progress = createProgress(1, ProblemProgressStatus.WRONG);
        Instant judgedAt = Instant.parse("2026-09-21T01:00:00Z");

        // when
        progress.changeSolvedAt(ProblemProgressStatus.WRONG, judgedAt);

        // then
        assertThat(progress.getSolvedAt()).isNull();
    }

    @Test
    @DisplayName("채점 결과가 정답이면 상태를 CORRECT로 변경한다")
    void changeStatusCorrect_changesStatus() {
        // given
        ProblemProgress progress = createProgress(1, ProblemProgressStatus.WRONG);

        // when
        progress.changeStatusCorrect();

        // then
        assertThat(progress.getProgressStatus()).isEqualTo(ProblemProgressStatus.CORRECT);
    }

    @Test
    @DisplayName("채점 결과가 오답이면 상태를 WRONG으로 변경한다")
    void changeStatusWrong_changesStatus() {
        // given
        ProblemProgress progress = createProgress(1, ProblemProgressStatus.CORRECT);

        // when
        progress.changeStatusWrong();

        // then
        assertThat(progress.getProgressStatus()).isEqualTo(ProblemProgressStatus.WRONG);
    }

    @Test
    @DisplayName("더 이른 채점 이벤트가 도착하면 createdAt을 최초 채점 시각으로 보정한다")
    void changeCreatedAt_earlierJudgedAt_changesCreatedAt() {
        // given
        Instant initialJudgedAt = Instant.parse("2026-09-21T02:00:00Z");
        Instant earlierJudgedAt = Instant.parse("2026-09-21T01:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                2,
                ProblemProgressStatus.WRONG,
                initialJudgedAt
        );

        // when
        progress.changeCreatedAt(earlierJudgedAt);

        // then
        assertThat(progress.getCreatedAt()).isEqualTo(earlierJudgedAt);
    }

    @Test
    @DisplayName("현재 createdAt보다 늦은 채점 이벤트는 최초 채점 시각을 변경하지 않는다")
    void changeCreatedAt_laterJudgedAt_keepsCreatedAt() {
        // given
        Instant initialJudgedAt = Instant.parse("2026-09-21T01:00:00Z");
        Instant laterJudgedAt = Instant.parse("2026-09-21T02:00:00Z");

        ProblemProgress progress = ProblemProgress.create(
                userId,
                problemId,
                1,
                1,
                ProblemProgressStatus.WRONG,
                initialJudgedAt
        );

        // when
        progress.changeCreatedAt(laterJudgedAt);

        // then
        assertThat(progress.getCreatedAt()).isEqualTo(initialJudgedAt);
    }

    @Test
    @DisplayName("현재 attemptNo보다 큰 제출 시도이면 최신 제출로 판단한다")
    void isNewerAttempt_greaterAttempt_returnsTrue() {
        // given
        ProblemProgress progress = createProgress(2, ProblemProgressStatus.WRONG);

        // when
        boolean result = progress.isNewerAttempt(3);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("현재 attemptNo와 같거나 작은 제출 시도이면 최신 제출이 아니다")
    void isNewerAttempt_sameOrOlderAttempt_returnsFalse() {
        // given
        ProblemProgress progress = createProgress(2, ProblemProgressStatus.WRONG);

        // when & then
        assertThat(progress.isNewerAttempt(2)).isFalse();
        assertThat(progress.isNewerAttempt(1)).isFalse();
    }

    private ProblemProgress createProgress(
            int attemptNo,
            ProblemProgressStatus status
    ) {
        return ProblemProgress.create(
                userId,
                problemId,
                1,
                attemptNo,
                status,
                Instant.parse("2026-09-21T00:00:00Z")
        );
    }

    private void assertBusinessException(
            Runnable action,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );
    }
}