package com.maesamco.judge.domain.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * ProblemPublished 이벤트 소비로 채워지는 "문제 실행 명세" 캐시.
 *
 * "생성 후 수정 없음(캐시성)" 유형: 새 버전이 발행되면
 * 기존 행을 수정하지 않고 새 행을 추가한다. updatedAt/deletedAt을 두지 않습니다.
 *
 * problemId + problemVersionId 조합이 논리적 유니크키. 제출 접수 시
 * problemId로 이 테이블에서 "가장 최근 발행된" 행을 조회해 problemVersionId를 확정하고,
 * 행 자체가 없으면 404 PROBLEM_NOT_FOUND로 응답합니다.
 */
@Entity
@Table(
        name = "p_problem_execution_specs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"problem_id", "problem_version_id"})
)@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemExecutionSpec {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "problem_id", nullable = false, updatable = false)
    private UUID problemId;

    // Content Service가 발행한 문제 버전 식별자 — 새 버전마다 새 행
    @Column(name = "problem_version_id", nullable = false, updatable = false)
    private UUID problemVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, updatable = false, length = 20)
    private SubmissionLanguage language;

    @Column(name = "starter_code", updatable = false, columnDefinition = "TEXT")
    private String starterCode;

    @Column(name = "test_cases", nullable = false, updatable = false, columnDefinition = "JSONB")
    private String testCases;

    @Column(name = "time_limit_ms", nullable = false, updatable = false)
    private int timeLimitMs;

    @Column(name = "memory_limit_mb", nullable = false, updatable = false)
    private int memoryLimitMb;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    private ProblemExecutionSpec(
            UUID problemId,
            UUID problemVersionId,
            SubmissionLanguage language,
            String starterCode,
            String testCases,
            int timeLimitMs,
            int memoryLimitMb,
            Instant publishedAt
    ) {
        this.problemId = problemId;
        this.problemVersionId = problemVersionId;
        this.language = language;
        this.starterCode = starterCode;
        this.testCases = testCases;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.publishedAt = publishedAt;
    }

    /** ProblemPublished 이벤트 소비 시 호출 */
    public static ProblemExecutionSpec fromPublishedEvent(
            UUID problemId,
            UUID problemVersionId,
            SubmissionLanguage language,
            String starterCode,
            String testCases,
            int timeLimitMs,
            int memoryLimitMb,
            Instant publishedAt
    ) {
        return new ProblemExecutionSpec(
                problemId, problemVersionId, language, starterCode, testCases,
                timeLimitMs, memoryLimitMb, publishedAt
        );
    }
}