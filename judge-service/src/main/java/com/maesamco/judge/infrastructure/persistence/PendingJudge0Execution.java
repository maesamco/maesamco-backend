package com.maesamco.judge.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Judge0 batch 제출 후 결과 대기 중인 토큰 매핑.
 * 결과가 반영되면(폴링 스케줄러가 처리) 즉시 삭제되는 임시 워킹 큐 — 영속적인 이력이 아님.
 */
@Entity
@Table(name = "p_pending_judge0_executions",
        indexes = @Index(name = "idx_pending_judge0_executions_created_at", columnList = "created_at"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"submission_id", "test_case_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingJudge0Execution {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Column(name = "test_case_id", nullable = false, updatable = false)
    private UUID testCaseId;

    @Column(name = "judge0_token", nullable = false, updatable = false, unique = true)
    private String judge0Token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "is_public", nullable = false, updatable = false)
    private boolean isPublic;

    private PendingJudge0Execution(UUID submissionId, UUID testCaseId, String judge0Token, boolean isPublic) {
        this.submissionId = submissionId;
        this.testCaseId = testCaseId;
        this.judge0Token = judge0Token;
        this.createdAt = Instant.now();
        this.isPublic = isPublic;
    }

    public static PendingJudge0Execution create(UUID submissionId, UUID testCaseId, String judge0Token, boolean isPublic) {
        return new PendingJudge0Execution(submissionId, testCaseId, judge0Token, isPublic);
    }
}