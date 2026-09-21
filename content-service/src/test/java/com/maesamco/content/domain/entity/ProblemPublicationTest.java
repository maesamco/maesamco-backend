package com.maesamco.content.domain.entity;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProblemPublicationTest {

    @Nested
    @DisplayName("발행 심사 요청")
    class PublicationReview {

        @Test
        @DisplayName("DRAFT 문제에 발행 심사를 요청하면 REVIEW_PENDING 상태가 된다")
        void requestPublicationReview_changesStatusToReviewPending() {
            // given
            Problem problem =
                    createProblem(
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
        @DisplayName("DRAFT가 아닌 문제에 발행 심사를 요청하면 상태 전이 예외가 발생한다")
        void requestPublicationReview_whenNotDraft_throwsException(
                ProblemStatus problemStatus
        ) {
            // given
            Problem problem =
                    createProblem(
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
                    .isEqualTo(
                            problemStatus
                    );
        }
    }

    @Nested
    @DisplayName("발행 승인")
    class PublicationApproval {

        @Test
        @DisplayName("REVIEW_PENDING 문제를 승인하면 PUBLISHED 상태가 된다")
        void approvePublication_changesStatusToPublished() {
            // given
            Problem problem =
                    createProblem(
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
        @DisplayName("REVIEW_PENDING이 아닌 문제를 승인하면 상태 전이 예외가 발생한다")
        void approvePublication_whenNotReviewPending_throwsException(
                ProblemStatus problemStatus
        ) {
            // given
            Problem problem =
                    createProblem(
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
                    .isEqualTo(
                            problemStatus
                    );
        }
    }

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
                RunningTimeLimit.SECOND_1,
                RunningMemoryLimit.MB_128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                problemStatus
        );
    }
}