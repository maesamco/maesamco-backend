package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문제 발행 버전 스냅샷 생성 규칙을 검증합니다.
 */
class ProblemVersionTest {

    @Test
    @DisplayName(
            "문제 발행 버전을 생성하면 "
                    + "현재 문제 내용과 테스트케이스가 스냅샷에 저장된다"
    )
    void createPublishedVersion_snapshotsProblemAndTestCases() {
        // given
        UUID problemId = UUID.randomUUID();

        Problem problem = createProblem();

        UUID firstTestCaseId = UUID.randomUUID();
        UUID secondTestCaseId = UUID.randomUUID();

        List<ProblemVersion.TestCaseItem> testCases = List.of(
                new ProblemVersion.TestCaseItem(
                        firstTestCaseId,
                        true,
                        "1 2",
                        "3",
                        1
                ),
                new ProblemVersion.TestCaseItem(
                        secondTestCaseId,
                        false,
                        "10 20",
                        "30",
                        2
                )
        );

        Instant publishedAt = Instant.parse(
                "2026-09-08T00:00:00Z"
        );

        // when
        ProblemVersion problemVersion =
                ProblemVersion.createPublished(
                        problemId,
                        problem,
                        testCases,
                        publishedAt
                );

        // then
        assertThat(problemVersion.getProblemId())
                .isEqualTo(problemId);

        assertThat(problemVersion.getVersionNo())
                .isEqualTo(1);

        assertThat(problemVersion.getPublishedAt())
                .isEqualTo(publishedAt);

        ProblemVersion.ProblemVersionSnapshot snapshot =
                problemVersion.getContentSnapshot();

        assertThat(snapshot.title())
                .isEqualTo("두 수의 합");

        assertThat(snapshot.language())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(snapshot.difficulty())
                .isEqualTo(ProblemDifficulty.EASY);

        assertThat(snapshot.type())
                .isEqualTo(ProblemType.CODE);

        assertThat(snapshot.description())
                .isEqualTo(
                        "두 정수를 더한 값을 반환하세요."
                );

        assertThat(snapshot.starterCode())
                .isEqualTo("class Solution {}");

        assertThat(snapshot.runningTimeLimit())
                .isEqualTo(1);

        assertThat(snapshot.runningMemoryLimit())
                .isEqualTo(128);

        assertThat(snapshot.timerPolicy())
                .isEqualTo(TimerPolicy.APPLY60);

        assertThat(snapshot.source())
                .isEqualTo(
                        ProblemSource.HUMAN_AUTHORED
                );

        assertThat(snapshot.testCases())
                .containsExactlyElementsOf(testCases);
    }

    /**
     * 발행 버전 테스트용 문제를 생성합니다.
     */
    private Problem createProblem() {
        return Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 더한 값을 반환하세요.",
                "class Solution {}",
                1,
                128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.REVIEW_PENDING,
                1
        );
    }
}
