package com.maesamco.content.domain.entity;

import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.entity.problem.ProblemVersionSnapshot;
import com.maesamco.content.domain.entity.problem.ProblemVersionTestCaseItem;
import com.maesamco.content.domain.entity.problem.RunningMemoryLimit;
import com.maesamco.content.domain.entity.problem.RunningTimeLimit;
import com.maesamco.content.domain.entity.problem.TimerPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

        ReflectionTestUtils.setField(
                problem,
                "id",
                problemId
        );

        UUID firstTestCaseId = UUID.randomUUID();
        UUID secondTestCaseId = UUID.randomUUID();

        List<ProblemVersionTestCaseItem> testCases =
                List.of(
                        new ProblemVersionTestCaseItem(
                                firstTestCaseId,
                                true,
                                "1 2",
                                "3",
                                1
                        ),
                        new ProblemVersionTestCaseItem(
                                secondTestCaseId,
                                false,
                                "10 20",
                                "30",
                                2
                        )
                );

        Instant publishedAt =
                Instant.parse(
                        "2026-09-21T00:00:00Z"
                );

        // when
        ProblemVersion problemVersion =
                ProblemVersion.createPublished(
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

        assertThat(problemVersion.getProblemSnapshot())
                .isNotNull();

        ProblemVersionSnapshot snapshot =
                problemVersion.toVersionSnapshot();

        assertThat(snapshot.title())
                .isEqualTo("두 수의 합");

        assertThat(snapshot.language())
                .isEqualTo(
                        ProgrammingLanguage.JAVA
                );

        assertThat(snapshot.difficulty())
                .isEqualTo(
                        ProblemDifficulty.EASY
                );

        assertThat(snapshot.type())
                .isEqualTo(
                        ProblemType.CODE
                );

        assertThat(snapshot.description())
                .isEqualTo(
                        "두 정수를 더한 값을 반환하세요."
                );

        assertThat(snapshot.starterCode())
                .isEqualTo(
                        "class Solution {}"
                );

        assertThat(snapshot.runningTimeLimit())
                .isEqualTo(1);

        assertThat(snapshot.runningMemoryLimit())
                .isEqualTo(128);

        assertThat(snapshot.timerPolicy())
                .isEqualTo(
                        TimerPolicy.APPLY60
                );

        assertThat(snapshot.source())
                .isEqualTo(
                        ProblemSource.HUMAN_AUTHORED
                );

        assertThat(snapshot.testCases())
                .containsExactlyElementsOf(
                        testCases
                );
    }

    @Test
    @DisplayName("발행 버전 스냅샷은 공개 및 비공개 테스트케이스 정보를 모두 보존한다")
    void createPublishedVersion_preservesTestCaseData() {
        // given
        Problem problem = createProblem();

        ReflectionTestUtils.setField(
                problem,
                "id",
                UUID.randomUUID()
        );

        UUID publicTestCaseId = UUID.randomUUID();
        UUID hiddenTestCaseId = UUID.randomUUID();

        List<ProblemVersionTestCaseItem> testCases =
                List.of(
                        new ProblemVersionTestCaseItem(
                                publicTestCaseId,
                                true,
                                "1 2",
                                "3",
                                1
                        ),
                        new ProblemVersionTestCaseItem(
                                hiddenTestCaseId,
                                false,
                                null,
                                "30",
                                2
                        )
                );

        // when
        ProblemVersion problemVersion =
                ProblemVersion.createPublished(
                        problem,
                        testCases,
                        Instant.parse(
                                "2026-09-21T00:00:00Z"
                        )
                );

        ProblemVersionSnapshot snapshot =
                problemVersion.toVersionSnapshot();

        // then
        assertThat(snapshot.testCases())
                .hasSize(2);

        assertThat(snapshot.testCases().get(0).testCaseId())
                .isEqualTo(publicTestCaseId);

        assertThat(snapshot.testCases().get(0).isPublic())
                .isTrue();

        assertThat(snapshot.testCases().get(0).input())
                .isEqualTo("1 2");

        assertThat(snapshot.testCases().get(0).expectedOutput())
                .isEqualTo("3");

        assertThat(snapshot.testCases().get(0).displayOrder())
                .isEqualTo(1);

        assertThat(snapshot.testCases().get(1).testCaseId())
                .isEqualTo(hiddenTestCaseId);

        assertThat(snapshot.testCases().get(1).isPublic())
                .isFalse();

        assertThat(snapshot.testCases().get(1).input())
                .isNull();

        assertThat(snapshot.testCases().get(1).expectedOutput())
                .isEqualTo("30");

        assertThat(snapshot.testCases().get(1).displayOrder())
                .isEqualTo(2);
    }

    private Problem createProblem() {
        return Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 더한 값을 반환하세요.",
                "class Solution {}",
                RunningTimeLimit.SECOND_1,
                RunningMemoryLimit.MB_128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.REVIEW_PENDING
        );
    }
}