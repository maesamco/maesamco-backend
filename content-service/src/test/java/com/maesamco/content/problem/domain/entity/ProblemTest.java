package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.problem.domain.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemTest {

    @Test
    @DisplayName("문제를 생성하면 입력한 필드가 그대로 채워지고 현재 버전은 1이다")
    void create_setsFieldsAndInitialVersion() {
        // given
        String title = "두 수의 합";
        ProgrammingLanguage language = ProgrammingLanguage.JAVA;
        ProblemDifficulty difficulty = ProblemDifficulty.EASY;
        ProblemType type = ProblemType.CODE;
        String description = "두 정수를 입력받아 합을 출력하세요.";
        String starterCode = "public class Main {}";
        RunningTimeLimit runningTimeLimit = RunningTimeLimit.values()[0];
        RunningMemoryLimit runningMemoryLimit = RunningMemoryLimit.values()[0];
        TimerPolicy timerPolicy = TimerPolicy.NOT_APPLY_TIMEPOLICY;
        ProblemSource source = ProblemSource.HUMAN_AUTHORED;
        ProblemStatus problemStatus = ProblemStatus.DRAFT;

        // when
        Problem problem = Problem.create(
                title,
                language,
                difficulty,
                type,
                description,
                starterCode,
                runningTimeLimit,
                runningMemoryLimit,
                timerPolicy,
                source,
                problemStatus
        );

        // then
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getLanguage()).isEqualTo(language);
        assertThat(problem.getDifficulty()).isEqualTo(difficulty);
        assertThat(problem.getType()).isEqualTo(type);
        assertThat(problem.getDescription()).isEqualTo(description);
        assertThat(problem.getStarterCode()).isEqualTo(starterCode);
        assertThat(problem.getRunningTimeLimit()).isEqualTo(runningTimeLimit);
        assertThat(problem.getRunningMemoryLimit()).isEqualTo(runningMemoryLimit);
        assertThat(problem.getTimerPolicy()).isEqualTo(timerPolicy);
        assertThat(problem.getSource()).isEqualTo(source);
        assertThat(problem.getProblemStatus()).isEqualTo(problemStatus);
        assertThat(problem.getCurrentVersionNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("starterCode가 null이어도 문제를 생성할 수 있다")
    void create_allowsNullStarterCode() {
        // when
        Problem problem = Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 입력받아 합을 출력하세요.",
                null,
                RunningTimeLimit.values()[0],
                RunningMemoryLimit.values()[0],
                TimerPolicy.NOT_APPLY_TIMEPOLICY,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.DRAFT
        );

        // then
        assertThat(problem.getStarterCode()).isNull();
    }

    @Test
    @DisplayName("CODE가 아닌 문제 유형으로 생성하면 예외가 발생한다")
    void create_throwsWhenTypeIsNotCode() {

        assertThatThrownBy(
                () -> Problem.create(
                        "객관식 문제",
                        ProgrammingLanguage.JAVA,
                        ProblemDifficulty.EASY,
                        ProblemType.ONE_CHOICE,
                        "다음 중 올바른 답을 고르세요.",
                        null,
                        RunningTimeLimit.values()[0],
                        RunningMemoryLimit.values()[0],
                        TimerPolicy.NOT_APPLY_TIMEPOLICY,
                        ProblemSource.HUMAN_AUTHORED,
                        ProblemStatus.DRAFT
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_PROBLEM_TYPE
                );
    }

    @Test
    @DisplayName("문제 제목을 변경할 수 있다")
    void changeTitle_updatesTitle() {
        // given
        Problem problem = createProblem();

        // when
        problem.changeTitle("변경된 문제 제목");

        // then
        assertThat(problem.getTitle()).isEqualTo("변경된 문제 제목");
    }

    @Test
    @DisplayName("문제 언어를 변경할 수 있다")
    void changeLanguage_updatesLanguage() {
        // given
        Problem problem = createProblem();

        // when
        problem.changeLanguage(ProgrammingLanguage.PYTHON);

        // then
        assertThat(problem.getLanguage()).isEqualTo(ProgrammingLanguage.PYTHON);
    }

    @Test
    @DisplayName("문제 난이도를 변경할 수 있다")
    void changeDifficulty_updatesDifficulty() {
        // given
        Problem problem = createProblem();

        // when
        problem.changeDifficulty(ProblemDifficulty.HARD);

        // then
        assertThat(problem.getDifficulty()).isEqualTo(ProblemDifficulty.HARD);
    }

    @Test
    @DisplayName("CODE가 아닌 문제 유형으로 변경하면 예외가 발생한다")
    void changeType_throwsWhenTypeIsNotCode() {
        // given
        Problem problem = createProblem();

        // when & then
        assertThatThrownBy(
                () -> problem.changeType(
                        ProblemType.SHORT_ANSWER
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception)
                                .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.INVALID_PROBLEM_TYPE
                );

        assertThat(problem.getType())
                .isEqualTo(ProblemType.CODE);
    }

    @Test
    @DisplayName("문제 지문을 변경할 수 있다")
    void changeDescription_updatesDescription() {
        // given
        Problem problem = createProblem();

        // when
        problem.changeDescription("변경된 문제 지문");

        // then
        assertThat(problem.getDescription()).isEqualTo("변경된 문제 지문");
    }

    @Test
    @DisplayName("starterCode를 변경할 수 있다")
    void changeStarterCode_updatesStarterCode() {
        // given
        Problem problem = createProblem();

        // when
        problem.changeStarterCode("class Solution {}");

        // then
        assertThat(problem.getStarterCode()).isEqualTo("class Solution {}");
    }

    @Test
    @DisplayName("starterCode를 null로 변경할 수 있다")
    void changeStarterCode_allowsNull() {
        // given
        Problem problem = createProblem();

        // when
        problem.changeStarterCode(null);

        // then
        assertThat(problem.getStarterCode()).isNull();
    }

    @Test
    @DisplayName("실행 시간 제한을 변경할 수 있다")
    void changeRunningTimeLimit_updatesRunningTimeLimit() {
        // given
        Problem problem = createProblem();
        RunningTimeLimit newRunningTimeLimit = RunningTimeLimit.values()[1];

        // when
        problem.changeRunningTimeLimit(newRunningTimeLimit);

        // then
        assertThat(problem.getRunningTimeLimit())
                .isEqualTo(newRunningTimeLimit);
    }

    @Test
    @DisplayName("실행 메모리 제한을 변경할 수 있다")
    void changeRunningMemoryLimit_updatesRunningMemoryLimit() {
        // given
        Problem problem = createProblem();
        RunningMemoryLimit newRunningMemoryLimit = RunningMemoryLimit.values()[1];

        // when
        problem.changeRunningMemoryLimit(newRunningMemoryLimit);

        // then
        assertThat(problem.getRunningMemoryLimit())
                .isEqualTo(newRunningMemoryLimit);
    }

    @Test
    @DisplayName("타이머 정책을 변경할 수 있다")
    void changeTimerPolicy_updatesTimerPolicy() {
        // given
        Problem problem = createProblem();

        // when
        problem.changeTimerPolicy(TimerPolicy.APPLY60);

        // then
        assertThat(problem.getTimerPolicy())
                .isEqualTo(TimerPolicy.APPLY60);
    }

    @Test
    @DisplayName("문제 출처를 변경할 수 있다")
    void changeSource_updatesSource() {
        // given
        Problem problem = createProblem();

        // when
        problem.changeSource(ProblemSource.AI_ASSISTED);

        // then
        assertThat(problem.getSource())
                .isEqualTo(ProblemSource.AI_ASSISTED);
    }

    @Test
    @DisplayName("문제 상태를 DRAFT로 변경할 수 있다")
    void setProblemStatusDraft_changesStatusToDraft() {
        // given
        Problem problem = createProblem();
        problem.setProblemStatusReviewPending();

        // when
        problem.setProblemStatusDraft();

        // then
        assertThat(problem.getProblemStatus())
                .isEqualTo(ProblemStatus.DRAFT);
    }

    @Test
    @DisplayName("문제 상태를 REVIEW_PENDING으로 변경할 수 있다")
    void setProblemStatusReviewPending_changesStatusToReviewPending() {
        // given
        Problem problem = createProblem();

        // when
        problem.setProblemStatusReviewPending();

        // then
        assertThat(problem.getProblemStatus())
                .isEqualTo(ProblemStatus.REVIEW_PENDING);
    }

    @Test
    @DisplayName("REVIEW_PENDING 상태의 문제를 승인하면 PUBLISHED 상태로 변경된다")
    void approvePublication_changesReviewPendingToPublished() {
        // given
        Problem problem = createProblem();
        problem.setProblemStatusReviewPending();

        // when
        problem.approvePublication();

        // then
        assertThat(problem.getProblemStatus())
                .isEqualTo(ProblemStatus.PUBLISHED);
    }

    @Test
    @DisplayName("REVIEW_PENDING 상태가 아닌 문제를 승인하면 예외가 발생한다")
    void approvePublication_throwsWhenStatusIsNotReviewPending() {
        // given
        Problem problem = createProblem();

        // when & then
        assertThatThrownBy(problem::approvePublication)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PROBLEM_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("문제 버전을 증가시키면 현재 버전이 1 증가한다")
    void increaseVersion_increasesCurrentVersionNo() {
        // given
        Problem problem = createProblem();

        // when
        problem.increaseVersion();

        // then
        assertThat(problem.getCurrentVersionNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("문제 버전을 여러 번 증가시키면 호출한 횟수만큼 증가한다")
    void increaseVersion_increasesEveryTime() {
        // given
        Problem problem = createProblem();

        // when
        problem.increaseVersion();
        problem.increaseVersion();
        problem.increaseVersion();

        // then
        assertThat(problem.getCurrentVersionNo()).isEqualTo(4);
    }

    private Problem createProblem() {
        return Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 입력받아 합을 출력하세요.",
                "public class Main {}",
                RunningTimeLimit.values()[0],
                RunningMemoryLimit.values()[0],
                TimerPolicy.NOT_APPLY_TIMEPOLICY,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.DRAFT
        );
    }
}
