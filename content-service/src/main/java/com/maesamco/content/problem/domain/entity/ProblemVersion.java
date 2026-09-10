package com.maesamco.content.problem.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import com.maesamco.content.problem.domain.enums.RunningMemoryLimit;
import com.maesamco.content.problem.domain.enums.RunningTimeLimit;
import com.maesamco.content.problem.domain.enums.TimerPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 문제 버전 이력 엔티티입니다.
 *
 * <p>문제 생성·수정 시점의 문제 스냅샷을 보존하며,
 * 발행 승인 시에는 테스트케이스까지 포함한 스냅샷을 생성합니다.</p>
 */
@Entity
@Getter
@Table(
        name = "p_problem_versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "problem_id",
                                "version_no"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "problem_id",
            nullable = false,
            updatable = false
    )
    private UUID problemId;

    @Column(
            name = "version_no",
            nullable = false,
            updatable = false
    )
    private Integer versionNo;

    /**
     * 문제 버전 스냅샷입니다.
     *
     * <p>PR #121에서 확정된 DB 컬럼명인
     * problem_snapshot을 그대로 사용합니다.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "problem_snapshot",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode problemSnapshot;

    /**
     * 해당 버전이 생성된 시각입니다.
     *
     * <p>발행 승인으로 생성되는 버전의 경우 실제 발행 시각으로 사용합니다.</p>
     */
    @Column(
            name = "published_at",
            nullable = false,
            updatable = false
    )
    private Instant publishedAt;

    private ProblemVersion(
            UUID problemId,
            Integer versionNo,
            JsonNode problemSnapshot,
            Instant publishedAt
    ) {
        this.problemId = problemId;
        this.versionNo = versionNo;
        this.problemSnapshot = problemSnapshot;
        this.publishedAt = publishedAt;
    }

    /**
     * 주어진 JSON 스냅샷으로 문제 버전을 생성합니다.
     */
    public static ProblemVersion create(
            UUID problemId,
            Integer versionNo,
            JsonNode problemSnapshot
    ) {
        return new ProblemVersion(
                problemId,
                versionNo,
                problemSnapshot,
                Instant.now()
        );
    }

    /**
     * 현재 문제 상태의 일반 버전 스냅샷을 생성합니다.
     *
     * <p>ProblemService의 생성·수정 버전 관리에서 사용합니다.</p>
     */
    public static ProblemVersion snapshot(
            Problem problem
    ) {
        ObjectNode snapshot = createProblemSnapshot(problem);

        return new ProblemVersion(
                problem.getId(),
                problem.getCurrentVersionNo(),
                snapshot,
                Instant.now()
        );
    }

    /**
     * 문제 발행 승인 시 테스트케이스까지 포함한 버전 스냅샷을 생성합니다.
     */
    public static ProblemVersion createPublished(
            UUID problemId,
            Problem problem,
            List<TestCaseItem> testCases,
            Instant publishedAt
    ) {
        ObjectNode snapshot = createProblemSnapshot(problem);

        ArrayNode testCaseArray =
                JsonNodeFactory.instance.arrayNode();

        if (testCases != null) {
            for (TestCaseItem testCase : testCases) {
                ObjectNode testCaseNode =
                        JsonNodeFactory.instance.objectNode();

                testCaseNode.put(
                        "testCaseId",
                        testCase.testCaseId().toString()
                );
                testCaseNode.put(
                        "isPublic",
                        testCase.isPublic()
                );
                testCaseNode.put(
                        "input",
                        testCase.input()
                );
                testCaseNode.put(
                        "expectedOutput",
                        testCase.expectedOutput()
                );
                testCaseNode.put(
                        "displayOrder",
                        testCase.displayOrder()
                );

                testCaseArray.add(testCaseNode);
            }
        }

        snapshot.set(
                "testCases",
                testCaseArray
        );

        return new ProblemVersion(
                problemId,
                problem.getCurrentVersionNo(),
                snapshot,
                publishedAt
        );
    }

    /**
     * ProblemPublished 이벤트 생성 코드와의 호환을 위해
     * JSON 스냅샷을 타입이 있는 발행 스냅샷으로 변환합니다.
     */
    public ProblemVersionSnapshot getContentSnapshot() {
        JsonNode snapshot = this.problemSnapshot;

        List<TestCaseItem> testCases =
                new ArrayList<>();

        JsonNode testCaseNodes =
                snapshot.path("testCases");

        if (testCaseNodes.isArray()) {
            for (JsonNode testCaseNode : testCaseNodes) {
                testCases.add(
                        new TestCaseItem(
                                UUID.fromString(
                                        testCaseNode
                                                .path("testCaseId")
                                                .asText()
                                ),
                                testCaseNode
                                        .path("isPublic")
                                        .asBoolean(),
                                testCaseNode
                                        .path("input")
                                        .asText(),
                                testCaseNode
                                        .path("expectedOutput")
                                        .asText(),
                                testCaseNode
                                        .path("displayOrder")
                                        .asInt()
                        )
                );
            }
        }

        RunningTimeLimit runningTimeLimit =
                RunningTimeLimit.valueOf(
                        snapshot
                                .path("runningTimeLimit")
                                .asText()
                );

        RunningMemoryLimit runningMemoryLimit =
                RunningMemoryLimit.valueOf(
                        snapshot
                                .path("runningMemoryLimit")
                                .asText()
                );

        JsonNode starterCodeNode =
                snapshot.get("starterCode");

        String starterCode =
                starterCodeNode == null
                        || starterCodeNode.isNull()
                        ? null
                        : starterCodeNode.asText();

        return new ProblemVersionSnapshot(
                snapshot.path("title").asText(),
                ProgrammingLanguage.valueOf(
                        snapshot.path("language").asText()
                ),
                ProblemDifficulty.valueOf(
                        snapshot.path("difficulty").asText()
                ),
                ProblemType.valueOf(
                        snapshot.path("type").asText()
                ),
                snapshot.path("description").asText(),
                starterCode,
                runningTimeLimit.getSeconds(),
                runningMemoryLimit.getMegabytes(),
                TimerPolicy.valueOf(
                        snapshot.path("timerPolicy").asText()
                ),
                ProblemSource.valueOf(
                        snapshot.path("source").asText()
                ),
                List.copyOf(testCases)
        );
    }

    /**
     * Problem 엔티티의 현재 상태를 JSON으로 변환합니다.
     */
    private static ObjectNode createProblemSnapshot(
            Problem problem
    ) {
        ObjectNode snapshot =
                JsonNodeFactory.instance.objectNode();

        snapshot.put(
                "title",
                problem.getTitle()
        );

        snapshot.put(
                "language",
                problem.getLanguage().name()
        );

        snapshot.put(
                "difficulty",
                problem.getDifficulty().name()
        );

        snapshot.put(
                "type",
                problem.getType().name()
        );

        snapshot.put(
                "description",
                problem.getDescription()
        );

        snapshot.put(
                "starterCode",
                problem.getStarterCode()
        );

        snapshot.put(
                "runningTimeLimit",
                problem.getRunningTimeLimit().name()
        );

        snapshot.put(
                "runningMemoryLimit",
                problem.getRunningMemoryLimit().name()
        );

        snapshot.put(
                "timerPolicy",
                problem.getTimerPolicy().name()
        );

        snapshot.put(
                "source",
                problem.getSource().name()
        );

        snapshot.put(
                "problemStatus",
                problem.getProblemStatus().name()
        );

        return snapshot;
    }

    /**
     * ProblemPublished 이벤트 생성에 사용하는 타입이 지정된 스냅샷입니다.
     */
    public record ProblemVersionSnapshot(
            String title,
            ProgrammingLanguage language,
            ProblemDifficulty difficulty,
            ProblemType type,
            String description,
            String starterCode,
            Integer runningTimeLimit,
            Integer runningMemoryLimit,
            TimerPolicy timerPolicy,
            ProblemSource source,
            List<TestCaseItem> testCases
    ) {

        public ProblemVersionSnapshot {
            testCases =
                    testCases == null
                            ? List.of()
                            : List.copyOf(testCases);
        }
    }

    /**
     * 발행 버전에 포함되는 테스트케이스 한 건입니다.
     */
    public record TestCaseItem(
            UUID testCaseId,
            boolean isPublic,
            String input,
            String expectedOutput,
            int displayOrder
    ) {
    }
}
