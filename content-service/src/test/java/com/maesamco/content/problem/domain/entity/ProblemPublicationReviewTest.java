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
 * 문제 발행 심사 상태 전이 규칙을 검증합니다.
 */
class ProblemPublicationReviewTest {

    @Test
    @DisplayName(
            "DRAFT 문제에 발행 심사를 요청하면 "
                    + "REVIEW_PENDING 상태가 된다"
    )
    void requestPublicationReview_changesStatusToReviewPending() {
        // given
        Problem problem = createProblem(
                ProblemStatus.DRAFT
        );

        // when
        problem.requestPublicationReview();

        // then
        assertThat(problem.getProblemStatus())
                .isEqualTo(
                        ProblemStatus.REVIEW_PENDING
                );
    }

    @ParameterizedTest
    @EnumSource(
            value = ProblemStatus.class,
            names = "DRAFT",
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName(
            "DRAFT가 아닌 문제에 발행 심사를 요청하면 "
                    + "상태 전이 예외가 발생한다"
    )
    void requestPublicationReview_whenNotDraft_throwsException(
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
                        problem::requestPublicationReview
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
     * 발행 심사 테스트용 문제를 생성합니다.
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
