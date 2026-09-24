package com.maesamco.content.domain.entity;

import com.maesamco.content.domain.entity.problem.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemVersionSnapshotMapperTest {

    @Test
    @DisplayName("Problem을 일반 JSON 스냅샷으로 변환하면 모든 문제 정보가 저장된다")
    void toJson_problem_createsSnapshot() {
        // given
        Problem problem = createProblem();

        // when
        JsonNode snapshot = ProblemVersionSnapshotMapper.toJson(problem);

        // then
        assertThat(snapshot.path("title").asString())
                .isEqualTo("두 수의 합");

        assertThat(snapshot.path("language").asString())
                .isEqualTo("JAVA");

        assertThat(snapshot.path("difficulty").asString())
                .isEqualTo("EASY");

        assertThat(snapshot.path("type").asString())
                .isEqualTo("CODE");

        assertThat(snapshot.path("description").asString())
                .isEqualTo("두 정수를 더한 값을 반환하세요.");

        assertThat(snapshot.path("starterCode").asString())
                .isEqualTo("class Solution {}");

        assertThat(snapshot.path("runningTimeLimit").asString())
                .isEqualTo("SECOND_1");

        assertThat(snapshot.path("runningMemoryLimit").asString())
                .isEqualTo("MB_128");

        assertThat(snapshot.path("timerPolicy").asString())
                .isEqualTo("APPLY60");

        assertThat(snapshot.path("source").asString())
                .isEqualTo("HUMAN_AUTHORED");

        assertThat(snapshot.path("problemStatus").asString())
                .isEqualTo("REVIEW_PENDING");

        assertThat(snapshot.has("testCases"))
                .isFalse();
    }

    @Test
    @DisplayName("Problem과 테스트케이스를 발행 JSON 스냅샷으로 변환한다")
    void toPublishedJson_problemAndTestCases_createsPublishedSnapshot() {
        // given
        Problem problem = createProblem();

        UUID firstTestCaseId = UUID.randomUUID();
        UUID secondTestCaseId = UUID.randomUUID();

        List<ProblemVersionTestCaseItem> testCases = List.of(
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

        // when
        JsonNode snapshot =
                ProblemVersionSnapshotMapper.toPublishedJson(
                        problem,
                        testCases
                );

        // then
        assertThat(snapshot.path("title").asString())
                .isEqualTo("두 수의 합");

        assertThat(snapshot.path("problemStatus").asString())
                .isEqualTo("REVIEW_PENDING");

        JsonNode testCaseNodes = snapshot.path("testCases");

        assertThat(testCaseNodes.isArray())
                .isTrue();

        assertThat(testCaseNodes)
                .hasSize(2);

        JsonNode first = testCaseNodes.get(0);

        assertThat(first.path("testCaseId").asString())
                .isEqualTo(firstTestCaseId.toString());

        assertThat(first.path("isPublic").asBoolean())
                .isTrue();

        assertThat(first.path("input").asString())
                .isEqualTo("1 2");

        assertThat(first.path("expectedOutput").asString())
                .isEqualTo("3");

        assertThat(first.path("displayOrder").asInt())
                .isEqualTo(1);

        JsonNode second = testCaseNodes.get(1);

        assertThat(second.path("testCaseId").asString())
                .isEqualTo(secondTestCaseId.toString());

        assertThat(second.path("isPublic").asBoolean())
                .isFalse();

        assertThat(second.path("input").asString())
                .isEqualTo("10 20");

        assertThat(second.path("expectedOutput").asString())
                .isEqualTo("30");

        assertThat(second.path("displayOrder").asInt())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("일반 JSON 스냅샷을 타입이 지정된 ProblemVersionSnapshot으로 변환한다")
    void fromJson_normalSnapshot_convertsToTypedSnapshot() {
        // given
        Problem problem = createProblem();

        JsonNode json =
                ProblemVersionSnapshotMapper.toJson(problem);

        // when
        ProblemVersionSnapshot snapshot =
                ProblemVersionSnapshotMapper.fromJson(json);

        // then
        assertThat(snapshot.title())
                .isEqualTo("두 수의 합");

        assertThat(snapshot.language())
                .isEqualTo(ProgrammingLanguage.JAVA);

        assertThat(snapshot.difficulty())
                .isEqualTo(ProblemDifficulty.EASY);

        assertThat(snapshot.type())
                .isEqualTo(ProblemType.CODE);

        assertThat(snapshot.description())
                .isEqualTo("두 정수를 더한 값을 반환하세요.");

        assertThat(snapshot.starterCode())
                .isEqualTo("class Solution {}");

        assertThat(snapshot.runningTimeLimit())
                .isEqualTo(1);

        assertThat(snapshot.runningMemoryLimit())
                .isEqualTo(128);

        assertThat(snapshot.timerPolicy())
                .isEqualTo(TimerPolicy.APPLY60);

        assertThat(snapshot.source())
                .isEqualTo(ProblemSource.HUMAN_AUTHORED);

        assertThat(snapshot.testCases())
                .isEmpty();
    }

    @Test
    @DisplayName("발행 JSON 스냅샷을 역변환하면 테스트케이스까지 복원한다")
    void fromJson_publishedSnapshot_restoresTestCases() {
        // given
        Problem problem = createProblem();

        UUID firstTestCaseId = UUID.randomUUID();
        UUID secondTestCaseId = UUID.randomUUID();

        List<ProblemVersionTestCaseItem> testCases = List.of(
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
                        null,
                        null,
                        2
                )
        );

        JsonNode json =
                ProblemVersionSnapshotMapper.toPublishedJson(
                        problem,
                        testCases
                );

        // when
        ProblemVersionSnapshot snapshot =
                ProblemVersionSnapshotMapper.fromJson(json);

        // then
        assertThat(snapshot.testCases())
                .containsExactlyElementsOf(testCases);

        assertThat(snapshot.testCases().get(0).testCaseId())
                .isEqualTo(firstTestCaseId);

        assertThat(snapshot.testCases().get(0).isPublic())
                .isTrue();

        assertThat(snapshot.testCases().get(0).input())
                .isEqualTo("1 2");

        assertThat(snapshot.testCases().get(0).expectedOutput())
                .isEqualTo("3");

        assertThat(snapshot.testCases().get(0).displayOrder())
                .isEqualTo(1);

        assertThat(snapshot.testCases().get(1).testCaseId())
                .isEqualTo(secondTestCaseId);

        assertThat(snapshot.testCases().get(1).isPublic())
                .isFalse();

        assertThat(snapshot.testCases().get(1).input())
                .isNull();

        assertThat(snapshot.testCases().get(1).expectedOutput())
                .isNull();

        assertThat(snapshot.testCases().get(1).displayOrder())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("starterCode가 null이면 JSON과 타입 스냅샷에서도 null을 유지한다")
    void nullableStarterCode_preservesNull() {
        // given
        Problem problem = Problem.create(
                "두 수의 합",
                ProgrammingLanguage.JAVA,
                ProblemDifficulty.EASY,
                ProblemType.CODE,
                "두 정수를 더한 값을 반환하세요.",
                null,
                RunningTimeLimit.SECOND_1,
                RunningMemoryLimit.MB_128,
                TimerPolicy.APPLY60,
                ProblemSource.HUMAN_AUTHORED,
                ProblemStatus.REVIEW_PENDING
        );

        // when
        JsonNode json =
                ProblemVersionSnapshotMapper.toJson(problem);

        ProblemVersionSnapshot snapshot =
                ProblemVersionSnapshotMapper.fromJson(json);

        // then
        assertThat(json.get("starterCode").isNull())
                .isTrue();

        assertThat(snapshot.starterCode())
                .isNull();
    }

    @Test
    @DisplayName("일반 스냅샷을 JSON으로 변환한 뒤 역변환해도 문제 정보가 유지된다")
    void toJsonAndFromJson_roundTrip_preservesProblemData() {
        // given
        Problem problem = createProblem();

        // when
        ProblemVersionSnapshot snapshot =
                ProblemVersionSnapshotMapper.fromJson(
                        ProblemVersionSnapshotMapper.toJson(problem)
                );

        // then
        assertThat(snapshot.title())
                .isEqualTo(problem.getTitle());

        assertThat(snapshot.language())
                .isEqualTo(problem.getLanguage());

        assertThat(snapshot.difficulty())
                .isEqualTo(problem.getDifficulty());

        assertThat(snapshot.type())
                .isEqualTo(problem.getType());

        assertThat(snapshot.description())
                .isEqualTo(problem.getDescription());

        assertThat(snapshot.starterCode())
                .isEqualTo(problem.getStarterCode());

        assertThat(snapshot.runningTimeLimit())
                .isEqualTo(
                        problem.getRunningTimeLimit().getSeconds()
                );

        assertThat(snapshot.runningMemoryLimit())
                .isEqualTo(
                        problem.getRunningMemoryLimit().getMegabytes()
                );

        assertThat(snapshot.timerPolicy())
                .isEqualTo(problem.getTimerPolicy());

        assertThat(snapshot.source())
                .isEqualTo(problem.getSource());

        assertThat(snapshot.testCases())
                .isEmpty();
    }

    @Test
    @DisplayName("null Problem은 일반 JSON 스냅샷으로 변환할 수 없다")
    void toJson_nullProblem_throwsException() {
        // when & then
        assertThatThrownBy(
                () -> ProblemVersionSnapshotMapper.toJson(null)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("problem must not be null");
    }

    @Test
    @DisplayName("null 테스트케이스 목록으로 발행 JSON 스냅샷을 생성할 수 없다")
    void toPublishedJson_nullTestCases_throwsException() {
        // given
        Problem problem = createProblem();

        // when & then
        assertThatThrownBy(
                () ->
                        ProblemVersionSnapshotMapper.toPublishedJson(
                                problem,
                                null
                        )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("testCases must not be null");
    }

    @Test
    @DisplayName("테스트케이스 목록에 null 요소가 있으면 발행 JSON 스냅샷을 생성할 수 없다")
    void toPublishedJson_nullTestCase_throwsException() {
        // given
        Problem problem = createProblem();

        List<ProblemVersionTestCaseItem> testCases =
                java.util.Arrays.asList(
                        new ProblemVersionTestCaseItem(
                                UUID.randomUUID(),
                                true,
                                "1 2",
                                "3",
                                1
                        ),
                        null
                );

        // when & then
        assertThatThrownBy(
                () ->
                        ProblemVersionSnapshotMapper.toPublishedJson(
                                problem,
                                testCases
                        )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("testCase must not be null");
    }

    @Test
    @DisplayName("null JSON은 ProblemVersionSnapshot으로 변환할 수 없다")
    void fromJson_nullSnapshot_throwsException() {
        // when & then
        assertThatThrownBy(
                () ->
                        ProblemVersionSnapshotMapper.fromJson(null)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("snapshot must not be null");
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