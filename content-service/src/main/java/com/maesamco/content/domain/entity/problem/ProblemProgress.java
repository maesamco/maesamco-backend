package com.maesamco.content.domain.entity.problem;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(
        name = "p_problem_progress",
        schema = "content_schema",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_p_problem_progress_user_problem",
                        columnNames = {
                                "user_id",
                                "problem_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_p_problem_progress_problem_id",
                        columnList = "problem_id"
                ),
                @Index(
                        name = "idx_p_problem_progress_user_created",
                        columnList = "user_id, created_at"
                ),
                @Index(
                        name = "idx_p_problem_progress_user_status_created",
                        columnList = "user_id, progress_status, created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemProgress {

    /** 무작위 key값 */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** 사용자 id */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 문제 id */
    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    /** 채점했을 때의 문제의 버전 번호 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 현재 ProblemProgress에 마지막으로 반영된 제출 시도 번호 */
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    /** (problem_id, user_id) 기준 최초 CORRECT 판정의 judgedAt */
    @Column(name = "solved_at")
    private Instant solvedAt;

    /** 맞는 경우 CORRECT, 틀리면 WRONG */
    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", nullable = false, length = 20)
    private ProblemProgressStatus progressStatus;

    /** (problem_id, user_id) 기준 최초 채점 결과의 judgedAt */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 동시 수정 충돌 감지를 위한 낙관적 락 버전 */
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    private ProblemProgress(
            UUID id,
            UUID userId, UUID problemId,
            Integer versionNo, Integer attemptNo,
            Instant solvedAt, ProblemProgressStatus progressStatus, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.problemId = problemId;
        this.versionNo = versionNo;
        this.attemptNo = attemptNo;
        this.solvedAt = solvedAt;
        this.progressStatus = progressStatus;
        this.createdAt = createdAt;
    }

    /** 최초 채점 결과를 기준으로 문제 풀이 진행 상태를 생성합니다. */
    public static ProblemProgress create(
            UUID userId, UUID problemId,
            Integer versionNo, Integer attemptNo,
            ProblemProgressStatus progressStatus, Instant judgedAt) {

        validateVersionNo(versionNo);
        validateAttemptNo(attemptNo);
        validateCreate(userId, problemId, progressStatus, judgedAt);

        // 최초로 생성하면서, CORRECT인 경우 실제 최초 채점 완료 시각을 solvedAt에 저장합니다.
        Instant solvedAt = progressStatus == ProblemProgressStatus.CORRECT ? judgedAt : null;

        return new ProblemProgress(UUID.randomUUID(),
                userId, problemId,
                versionNo, attemptNo,
                solvedAt, progressStatus, judgedAt
        );
    }

    /** 버전 번호 변경 */
    public void changeVersionNo(Integer versionNo) {
        validateVersionNo(versionNo);
        if (this.versionNo.equals(versionNo))
            return ;
        this.versionNo = versionNo;
    }

    /** 마지막으로 반영된 제출 시도 번호 변경 */
    public void changeAttemptNo(Integer attemptNo) {
        validateAttemptNo(attemptNo);

        if (attemptNo <= this.attemptNo)
            return;

        this.attemptNo = attemptNo;
    }

    /** 최초 문제 풀어서 맞춘 시각 저장 */
    public void changeSolvedAt(ProblemProgressStatus progressStatus, Instant judgedAt) {
        if (progressStatus != ProblemProgressStatus.CORRECT || judgedAt == null)
            return ;
        if (this.solvedAt == null || judgedAt.isBefore(this.solvedAt))
            this.solvedAt = judgedAt;
    }

    /** 채점 결과가 정답이면 CORRECT 상태로 변경합니다. */
    public void changeStatusCorrect() {
        this.progressStatus = ProblemProgressStatus.CORRECT;
    }

    /** 채점 결과가 오답이면 WRONG 상태로 변경합니다. */
    public void changeStatusWrong() {
        this.progressStatus = ProblemProgressStatus.WRONG;
    }

    /** 최초 ProblemProgress 발생 시각을 더 이른 채점 시각으로 보정합니다. */
    public void changeCreatedAt(Instant judgedAt) {
        if (judgedAt == null) {
            return ;
        }

        if (this.createdAt == null || judgedAt.isBefore(this.createdAt)) {
            this.createdAt = judgedAt;
        }
    }

    private static void validateCreate(UUID userId, UUID problemId, ProblemProgressStatus progressStatus, Instant judgedAt) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PROBLEM_PROGRESS_INVALID_USER_ID);
        }

        if (problemId == null) {
            throw new BusinessException(ErrorCode.PROBLEM_PROGRESS_INVALID_PROBLEM_ID);
        }

        if (progressStatus == null) {
            throw new BusinessException(ErrorCode.PROBLEM_PROGRESS_INVALID_STATUS);
        }

        if (judgedAt == null) {
            throw new BusinessException(ErrorCode.PROBLEM_PROGRESS_INVALID_JUDGED_AT);
        }
    }


    /** 버전 번호가 1이상 이어야 한다. */
    private static void validateVersionNo(Integer versionNo) {
        if (versionNo == null || versionNo < 1) {
            throw new BusinessException(ErrorCode.PROBLEM_PROGRESS_INVALID_VERSION_NO);
        }
    }

    /** 제출 시도 번호가 1이상 이어야 한다. */
    private static void validateAttemptNo(Integer attemptNo) {
        if (attemptNo == null || attemptNo < 1) {
            throw new BusinessException(ErrorCode.PROBLEM_PROGRESS_INVALID_ATTEMPT_NO);
        }
    }

    /** 현재 반영된 제출보다 최신 제출인지 확인합니다. */
    public boolean isNewerAttempt(Integer attemptNo) {
        validateAttemptNo(attemptNo);
        return attemptNo > this.attemptNo;
    }
}