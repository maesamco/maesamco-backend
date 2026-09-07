package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 문제 발행 승인 상태 전이 규칙을 검증합니다.
 */
class ProblemPublicationApprovalTest {

    @Test
    @DisplayName(
            "REVIEW_PENDING 문제를 승인하면 "
                    + "PUBLISHED 상태가 된다"
    )
    void approvePublication_changesStatusToPublished() {
        // given
        Problem problem = createProblem(
                ProblemStatus.REVIEW_PENDING
        );

        // when
        problem.approvePublication();

        // then
        assertThat(problem.getProblemStatus())
                .isEqualTo(
                        ProblemStatus.PUBLISHED
                );
    }

    @ParameterizedTest
    @EnumSource(
            value = ProblemStatus.class,
            names = "REVIEW_PENDING",
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName(
            "REVIEW_PENDING이 아닌 문제를 승인하면 "
                    + "상태 전이 예외가 발생한다"
    )
    void approvePublication_whenNotReviewPending_throwsException(
            ProblemStatus problemStatus
    ) {
        // given
        Problem problem = createProblem(
                problemStatus
        );

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        problem::approvePublication
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ErrorCode.INVALID_PROBLEM_STATUS_TRANSITION
                );

        assertThat(problem.getProblemStatus())
                .isEqualTo(problemStatus);
    }

    /**
     * 발행 승인 테스트용 문제를 생성합니다.
     */
    private Problem createProblem(
            ProblemStatus problemStatus
    ) {
        return Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수의 합을 반환하세요.",
                "class Solution {}",
                1,
                128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                problemStatus,
                1
        );
    }
}
