package com.maesamco.content.problem.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_problem_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemTag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    private ProblemTag(UUID problemId, UUID tagId) {
        this.problemId = problemId;
        this.tagId = tagId;
    }

    /** 문제-태그 연결 생성 */
    public static ProblemTag create(UUID problemId, UUID tagId) {
        return new ProblemTag(problemId, tagId);
    }

    // problem 과 tag의 중간 Entity이며, BaseEntity를 상속받지 않고 Hard Delete를 한다.
}