package com.maesamco.content.problem.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "problem_snapshot",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private JsonNode problemSnapshot;

    private ProblemVersion(
            UUID problemId,
            Integer versionNo,
            JsonNode problemSnapshot
    ) {
        this.problemId = problemId;
        this.versionNo = versionNo;
        this.problemSnapshot = problemSnapshot;
    }

    /** 문제 버전 이력을 생성합니다. */
    public static ProblemVersion create(
            UUID problemId,
            Integer versionNo,
            JsonNode problemSnapshot
    ) {
        return new ProblemVersion(
                problemId,
                versionNo,
                problemSnapshot
        );
    }

    /** 현재 문제 상태의 스냅샷을 생성합니다. */
    public static ProblemVersion snapshot(
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

        return new ProblemVersion(
                problem.getId(),
                problem.getCurrentVersionNo(),
                snapshot
        );
    }
}
