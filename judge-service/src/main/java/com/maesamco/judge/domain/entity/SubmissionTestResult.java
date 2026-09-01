package com.maesamco.judge.domain.entity;

import jakarta.persistence.*;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 제출 1건에 종속된 개별 테스트케이스 채점 결과.
 *
 * 제출 자체가 불변이라 채점 결과도 불변이므로 updatedAt/updatedBy, deletedAt/deletedBy를 두지 않고
 * createdAt만 남깁니다.
 *
 * testCaseId는 Content Service 소유 리소스라 논리 FK(UUID)로만 저장합니다.
 * submissionId만 같은 DB 안의 실제 FK.
 */
@Entity
@Table(name = "p_submission_test_results",
       indexes = @Index(name = "idx_submission_test_results_submission", columnList = "submission_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionTestResult {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private Submission submission;

    @Column(name = "test_case_id", nullable = false, updatable = false)
    private UUID testCaseId;

    @Column(name = "is_public", nullable = false, updatable = false)
    private boolean isPublic;

    @Column(name = "passed", nullable = false, updatable = false)
    private boolean passed;

    // isPublic = true 인 경우에만 값이 채워지고, API 응답에도 이 조건일 때만 노출한다.
    @Column(name = "actual_output", columnDefinition = "TEXT", updatable = false)
    private String actualOutput;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 30, updatable = false)
    private SubmissionTestErrorType errorType;

    private SubmissionTestResult(
            Submission submission,
            UUID testCaseId,
            boolean isPublic,
            boolean passed,
            String actualOutput,
            SubmissionTestErrorType errorType
    ) {
        this.submission = submission;
        this.testCaseId = testCaseId;
        this.isPublic = isPublic;
        this.passed = passed;
        // 비공개 테스트케이스는 실제 값을 저장하지 않음
        this.actualOutput = isPublic ? actualOutput : null;
        this.errorType = errorType;
    }

    public static SubmissionTestResult create(
            Submission submission,
            UUID testCaseId,
            boolean isPublic,
            boolean passed,
            String actualOutput,
            SubmissionTestErrorType errorType
    ) {
        return new SubmissionTestResult(submission, testCaseId, isPublic, passed, actualOutput, errorType);
    }
}