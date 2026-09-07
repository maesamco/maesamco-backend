package com.maesamco.content.problem.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** 문제 버전 이력 엔티티 */
@Entity
@Getter
@Table(name = "p_problem_versions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "problem_id", nullable = false, updatable = false)
    private UUID problemId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private Integer versionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "problem_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode problemSnapshot;

    private ProblemVersion(UUID problemId, Integer versionNo, JsonNode problemSnapshot) {
        this.problemId = problemId;
        this.versionNo = versionNo;
        this.problemSnapshot = problemSnapshot;
    }

    /** 문제 버전 이력을 생성한다. */
    public static ProblemVersion create(UUID problemId, Integer versionNo, JsonNode problemSnapshot) {
        return new ProblemVersion(problemId, versionNo, problemSnapshot);
    }

    /** 현재의 problem의 데이터에 대해 snapshot을 저장한다. */
    public static ProblemVersion snapshot(Problem problem) {

        ObjectNode snapshot = JsonNodeFactory.instance.objectNode();

        snapshot.put("title", problem.getTitle());
        snapshot.put("language", problem.getLanguage().name());
        snapshot.put("difficulty", problem.getDifficulty().name());
        snapshot.put("type", problem.getType().name());
        snapshot.put("description", problem.getDescription());
        snapshot.put("starterCode", problem.getStarterCode());
        snapshot.put("runningTimeLimit", problem.getRunningTimeLimit());
        snapshot.put("runningMemoryLimit", problem.getRunningMemoryLimit());
        snapshot.put("timerPolicy", problem.getTimerPolicy().name());
        snapshot.put("source", problem.getSource().name());
        snapshot.put("problemStatus", problem.getProblemStatus().name());

        return new ProblemVersion(problem.getId(), problem.getCurrentVersionNo(), snapshot);
    }

    // 문제 버전 이력은 수정이 불가하다.
}