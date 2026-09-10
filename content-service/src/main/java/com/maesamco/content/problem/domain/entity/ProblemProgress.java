package com.maesamco.content.problem.domain.entity;

import com.maesamco.content.problem.domain.enums.ProgressStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 문제 풀이 진행 엔티티입니다.
 *
 * <p>현재는 문제 풀이 이력 기능을 위한 도메인 모델과
 * 데이터베이스 제약만 준비되어 있습니다.</p>
 *
 * <p>Repository·Service·Controller 및 사용자용 API는
 * 문제 풀이 이력 기능 구현 시 후속 작업으로 추가합니다.</p>
 */
@Entity
@Getter
@Table(
        name = "p_problem_progress",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "user_id",
                                "problem_id"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "problem_id", nullable = false, updatable = false)
    private UUID problemId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "solved_at")
    private Instant solvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", nullable = false, length = 20)
    private ProgressStatus progressStatus;

    private ProblemProgress(UUID userId, UUID problemId, Integer versionNo, ProgressStatus progressStatus) {
        this.userId = userId;
        this.problemId = problemId;
        this.versionNo = versionNo;
        this.progressStatus = progressStatus;
    }

    /** 문제 풀이 진행 정보를 생성한다. */
    public static ProblemProgress create(UUID userId, UUID problemId, Integer versionNo) {
        return new ProblemProgress(userId, problemId, versionNo, ProgressStatus.NOT_ATTEMPTED);
    }

    /** 채점 기준 문제 버전을 변경한다. */
    public void changeVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    /** 문제 풀이 상태를 변경한다. */
    public void changeProgressStatus(ProgressStatus progressStatus) { this.progressStatus = progressStatus; }

    /** 문제를 최초로 시각을 기록한다. */
    public void firstSolved() {
        this.solvedAt = Instant.now();
        this.progressStatus = ProgressStatus.CORRECT;
    }
}
