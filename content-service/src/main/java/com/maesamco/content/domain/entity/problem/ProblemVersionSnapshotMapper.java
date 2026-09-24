package com.maesamco.content.domain.entity.problem;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.maesamco.content.domain.entity.ProgrammingLanguage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** ProblemVersion의 타입 객체와 JSON 스냅샷 간 변환을 담당합니다. */
public final class ProblemVersionSnapshotMapper {

    private ProblemVersionSnapshotMapper() { }

    /** Problem의 현재 상태를 일반 버전 JSON 스냅샷으로 변환합니다. */
    public static JsonNode toJson(Problem problem) {
        Objects.requireNonNull(problem, "problem must not be null");

        return createProblemSnapshot(problem);
    }

    /** Problem과 테스트케이스를 발행 버전 JSON 스냅샷으로 변환합니다. */
    public static JsonNode toPublishedJson(Problem problem, List<ProblemVersionTestCaseItem> testCases) {
        Objects.requireNonNull(problem, "problem must not be null");
        Objects.requireNonNull(testCases, "testCases must not be null");

        ObjectNode snapshot = createProblemSnapshot(problem);
        snapshot.set("testCases", createTestCaseArray(testCases));

        return snapshot;
    }

    /**
     * 저장된 JSON 스냅샷을 타입이 지정된 문제 버전 스냅샷으로 변환합니다.
     */
    public static ProblemVersionSnapshot fromJson(JsonNode snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        RunningTimeLimit runningTimeLimit = RunningTimeLimit.valueOf(
                snapshot.path("runningTimeLimit").asText()
        );

        RunningMemoryLimit runningMemoryLimit = RunningMemoryLimit.valueOf(
                snapshot.path("runningMemoryLimit").asText()
        );

        return new ProblemVersionSnapshot(
                snapshot.path("title").asText(),
                ProgrammingLanguage.valueOf(snapshot.path("language").asText()),
                ProblemDifficulty.valueOf(snapshot.path("difficulty").asText()),
                ProblemType.valueOf(snapshot.path("type").asText()),
                snapshot.path("description").asText(),
                getNullableText(snapshot, "starterCode"),
                runningTimeLimit.getSeconds(),
                runningMemoryLimit.getMegabytes(),
                TimerPolicy.valueOf(snapshot.path("timerPolicy").asText()),
                ProblemSource.valueOf(snapshot.path("source").asText()),
                toTestCaseItems(snapshot.path("testCases"))
        );
    }

    private static ObjectNode createProblemSnapshot(Problem problem) {
        ObjectNode snapshot = JsonNodeFactory.instance.objectNode();

        snapshot.put("title", problem.getTitle());
        snapshot.put("language", problem.getLanguage().name());
        snapshot.put("difficulty", problem.getDifficulty().name());
        snapshot.put("type", problem.getType().name());
        snapshot.put("description", problem.getDescription());
        snapshot.put("starterCode", problem.getStarterCode());
        snapshot.put("runningTimeLimit", problem.getRunningTimeLimit().name());
        snapshot.put("runningMemoryLimit", problem.getRunningMemoryLimit().name());
        snapshot.put("timerPolicy", problem.getTimerPolicy().name());
        snapshot.put("source", problem.getSource().name());
        snapshot.put("problemStatus", problem.getProblemStatus().name());

        return snapshot;
    }

    private static ArrayNode createTestCaseArray(List<ProblemVersionTestCaseItem> testCases) {
        ArrayNode testCaseArray = JsonNodeFactory.instance.arrayNode();

        for (ProblemVersionTestCaseItem testCase : testCases) {
            Objects.requireNonNull(testCase, "testCase must not be null");

            ObjectNode testCaseNode = JsonNodeFactory.instance.objectNode();

            testCaseNode.put("testCaseId", testCase.testCaseId().toString());
            testCaseNode.put("isPublic", testCase.isPublic());
            testCaseNode.put("input", testCase.input());
            testCaseNode.put("expectedOutput", testCase.expectedOutput());
            testCaseNode.put("displayOrder", testCase.displayOrder());

            testCaseArray.add(testCaseNode);
        }

        return testCaseArray;
    }

    private static List<ProblemVersionTestCaseItem> toTestCaseItems(JsonNode testCaseNodes) {
        if (!testCaseNodes.isArray()) {
            return List.of();
        }

        List<ProblemVersionTestCaseItem> testCases = new ArrayList<>();

        for (JsonNode testCaseNode : testCaseNodes) {
            testCases.add(
                    new ProblemVersionTestCaseItem(
                            UUID.fromString(testCaseNode.path("testCaseId").asText()),
                            testCaseNode.path("isPublic").asBoolean(),
                            getNullableText(testCaseNode, "input"),
                            getNullableText(testCaseNode, "expectedOutput"),
                            testCaseNode.path("displayOrder").asInt()
                    )
            );
        }

        return List.copyOf(testCases);
    }

    private static String getNullableText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);

        return (value == null || value.isNull()) ? null : value.asText();
    }
}