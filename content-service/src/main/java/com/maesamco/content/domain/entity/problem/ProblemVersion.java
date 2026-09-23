package com.maesamco.content.domain.entity.problem;

import com.fasterxml.jackson.databind.JsonNode;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
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
import java.util.List;
import java.util.Objects;
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
                        columnNames = {"problem_id", "version_no"}
                )
        }
)
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
    @Column(name = "problem_snapshot", nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode problemSnapshot;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    private ProblemVersion(UUID problemId, Integer versionNo, JsonNode problemSnapshot, Instant publishedAt) {
        this.problemId = Objects.requireNonNull(problemId, "problemId must not be null");
        this.versionNo = Objects.requireNonNull(versionNo, "versionNo must not be null");
        this.problemSnapshot = Objects.requireNonNull(problemSnapshot, "problemSnapshot must not be null");
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");

        if (versionNo < 1) {
            throw new BusinessException(ErrorCode.PROBLEM_VERSION_INVALID_VERSION_NO);
        }
    }

    /** 주어진 JSON 스냅샷으로 문제 버전을 생성합니다. */
    public static ProblemVersion create(UUID problemId, Integer versionNo, JsonNode problemSnapshot) {
        return new ProblemVersion(problemId, versionNo, problemSnapshot, Instant.now());
    }

    /** 현재 문제 상태의 일반 버전 스냅샷을 생성합니다. */
    public static ProblemVersion snapshot(Problem problem) {
        Objects.requireNonNull(problem, "problem must not be null");

        JsonNode snapshot = ProblemVersionSnapshotMapper.toJson(problem);

        return new ProblemVersion(problem.getId(), problem.getCurrentVersionNo(), snapshot, Instant.now());
    }

    /** 문제 발행 승인 시 테스트케이스까지 포함한 버전 스냅샷을 생성합니다. */
    public static ProblemVersion createPublished(
            Problem problem,
            List<ProblemVersionTestCaseItem> testCases,
            Instant publishedAt
    ) {
        Objects.requireNonNull(problem, "problem must not be null");
        Objects.requireNonNull(testCases, "testCases must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");

        if (testCases.isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_PUBLICATION_TEST_CASE_REQUIRED);
        }

        JsonNode snapshot = ProblemVersionSnapshotMapper.toPublishedJson(problem, testCases);

        return new ProblemVersion(problem.getId(), problem.getCurrentVersionNo(), snapshot, publishedAt);
    }

    /** 저장된 JSON 스냅샷을 타입이 지정된 문제 버전 스냅샷으로 변환합니다. */
    public ProblemVersionSnapshot toVersionSnapshot() {
        return ProblemVersionSnapshotMapper.fromJson(problemSnapshot);
    }
}