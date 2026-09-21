package com.maesamco.content.infrastructure.messaging.event;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemDifficulty;
import com.maesamco.content.domain.entity.problem.ProblemSource;
import com.maesamco.content.domain.entity.problem.ProblemStatus;
import com.maesamco.content.domain.entity.problem.ProblemType;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
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

class ProblemPublishedEventTest {

    @Test
    @DisplayName(
            "확정된 ProblemVersion으로 ProblemPublished 이벤트를 생성하면 "
                    + "채점 실행 명세가 올바르게 변환된다"
    )
    void fromPublishedVersion_createsEvent() {
        // given
        UUID problemId = UUID.randomUUID();
        UUID problemVersionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        UUID publicTestCaseId = UUID.randomUUID();
        UUID hiddenTestCaseId = UUID.randomUUID();

        Instant publishedAt =
                Instant.parse("2026-09-21T00:00:00Z");

        Instant occurredAt =
                Instant.parse("2026-09-21T00:00:01Z");

        Problem problem = createProblem();

        ReflectionTestUtils.setField(
                problem,
                "id",
                problemId
        );

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
                                "10 20",
                                "30",
                                2
                        )
                );

        ProblemVersion problemVersion =
                ProblemVersion.createPublished(
                        problem,
                        testCases,
                        publishedAt
                );

        // when
        ProblemPublishedEvent event =
                ProblemPublishedEvent.fromPublishedVersion(
                        eventId,
                        occurredAt,
                        problemVersionId,
                        problemVersion
                );

        // then
        assertThat(event.eventId())
                .isEqualTo(eventId);

        assertThat(event.eventType())
                .isEqualTo(
                        ProblemPublishedEvent.EVENT_TYPE
                );

        assertThat(event.eventVersion())
                .isEqualTo(
                        ProblemPublishedEvent.EVENT_VERSION
                );

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

        assertThat(event.timeLimit())
                .isEqualTo(1000);

        assertThat(event.memoryLimit())
                .isEqualTo(128);

        assertThat(event.publishedAt())
                .isEqualTo(publishedAt);

        assertThat(event.testCases())
                .containsExactly(
                        new ProblemPublishedTestCaseItem(
                                publicTestCaseId,
                                true,
                                "1 2",
                                "3",
                                1
                        ),
                        new ProblemPublishedTestCaseItem(
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
                RunningTimeLimit.SECOND_1,
                RunningMemoryLimit.MB_128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.REVIEW_PENDING
        );
    }
}