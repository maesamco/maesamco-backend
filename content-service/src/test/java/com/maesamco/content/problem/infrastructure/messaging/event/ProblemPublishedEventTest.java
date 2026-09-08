package com.maesamco.content.problem.infrastructure.messaging.event;

import com.maesamco.content.problem.domain.entity.Problem;
import com.maesamco.content.problem.domain.entity.ProblemVersion;
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
 * ProblemPublished 이벤트 생성 규칙을 검증합니다.
 */
class ProblemPublishedEventTest {

    @Test
    @DisplayName(
            "확정된 ProblemVersion으로 ProblemPublished 이벤트를 생성하면 "
                    + "채점 실행 명세가 올바르게 변환된다"
    )
    void fromPublishedVersion_createsEvent() {
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        UUID publicTestCaseId = UUID.randomUUID();
        UUID hiddenTestCaseId = UUID.randomUUID();

        Instant publishedAt =
                Instant.parse(
                        "2026-09-08T00:00:00Z"
                );

        Instant occurredAt =
                Instant.parse(
                        "2026-09-08T00:00:01Z"
                );

        Problem problem = createProblem();

        List<ProblemVersion.TestCaseItem> testCases =
                List.of(
                        new ProblemVersion.TestCaseItem(
                                publicTestCaseId,
                                true,
                                "1 2",
                                "3",
                                1
                        ),
                        new ProblemVersion.TestCaseItem(
                                hiddenTestCaseId,
                                false,
                                "10 20",
                                "30",
                                2
                        )
                );

        ProblemVersion problemVersion =
                ProblemVersion.createPublished(
                        problemId,
                        problem,
                        testCases,
                        publishedAt
                );

        ProblemPublishedEvent event =
                ProblemPublishedEvent.fromPublishedVersion(
                        eventId,
                        occurredAt,
                        problemVersionId,
                        problemVersion
                );

        assertThat(event.eventId())
                .isEqualTo(eventId);

        assertThat(event.eventType())
                .isEqualTo("PROBLEM_PUBLISHED");

        assertThat(event.eventVersion())
                .isEqualTo(1);

        assertThat(event.occurredAt())
                .isEqualTo(occurredAt);

        assertThat(event.problemId())
                .isEqualTo(problemId);

        assertThat(event.problemVersionId())
                .isEqualTo(problemVersionId);

        assertThat(event.versionNo())
                .isEqualTo(1);

        assertThat(event.language())
                .isEqualTo("JAVA");

        assertThat(event.starterCode())
                .isEqualTo("class Solution {}");

        /*
         * Content 도메인은 실행 시간을 초 단위로 저장하고,
         * Judge 실행 명세에는 밀리초 단위로 전달합니다.
         *
         * 1초 -> 1000ms
         */
        assertThat(event.timeLimit())
                .isEqualTo(1000);

        assertThat(event.memoryLimit())
                .isEqualTo(128);

        assertThat(event.publishedAt())
                .isEqualTo(publishedAt);

        assertThat(event.testCases())
                .containsExactly(
                        new ProblemPublishedEvent.TestCaseItem(
                                publicTestCaseId,
                                true,
                                "1 2",
                                "3",
                                1
                        ),
                        new ProblemPublishedEvent.TestCaseItem(
                                hiddenTestCaseId,
                                false,
                                "10 20",
                                "30",
                                2
                        )
                );
    }

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
