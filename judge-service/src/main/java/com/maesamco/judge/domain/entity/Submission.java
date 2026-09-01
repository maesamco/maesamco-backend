package com.maesamco.judge.domain.entity;

import com.maesamco.judge.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 코드 제출 및 채점 진행 상태를 나타내는 애그리거트 루트.
 *
 * 불변 보존형 — 제출 코드는 삭제하지 않고 보존한다는 원칙이라 소프트 삭제 대상이 아니지만,
 * BaseEntity 상속 자체는 유지해서 감사 컬럼(createdAt 등)은
 * 그대로 쓴다. deletedAt/deletedBy는 이 엔티티에서는 실제로 채워지지 않습니다.
 *
 * userId / problemId / problemVersionId는 다른 서비스(User/Content)의 식별자라
 * 논리 FK로만 저장합니다.
 */
@Entity
@Table(name = "p_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "problem_id", nullable = false, updatable = false)
    private UUID problemId;

    // 요청 시점엔 없고, p_problem_execution_specs에서 최신 발행 버전을 조회해 내부적으로 채움
    @Column(name = "problem_version_id", nullable = false, updatable = false)
    private UUID problemVersionId;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    // 최대 100KB
    @Column(name = "code", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, updatable = false, length = 20)
    private SubmissionLanguage language;

    @Column(name = "idempotency_key", nullable = false, updatable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status;

    // status = COMPLETED 일 때만 값 존재
    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 30)
    private SubmissionResult result;

    // status = FAILED 일 때만 값 존재. result와 동시에 값을 갖지 않음
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 30)
    private FailureCode failureCode;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "memory_used_kb")
    private Integer memoryUsedKb;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "judged_at")
    private Instant judgedAt;

    private Submission(
            UUID userId,
            UUID problemId,
            UUID problemVersionId,
            int attemptNo,
            String code,
            SubmissionLanguage language,
            String idempotencyKey
    ) {
        this.userId = userId;
        this.problemId = problemId;
        this.problemVersionId = problemVersionId;
        this.attemptNo = attemptNo;
        this.code = code;
        this.language = language;
        this.idempotencyKey = idempotencyKey;
        this.status = SubmissionStatus.PENDING;
        this.retryCount = 0;
        this.submittedAt = Instant.now();
    }

    /**
     * POST /api/v1/submissions 접수 시점에 PENDING 상태로 생성한다 (judge_service_api_spec.md 1-1절).
     * 이 생성자만으로 완결되므로 Outbox 레코드는 별도 Application 계층에서 같은 트랜잭션에 기록한다.
     */
    public static Submission create(
            UUID userId,
            UUID problemId,
            UUID problemVersionId,
            int attemptNo,
            String code,
            SubmissionLanguage language,
            String idempotencyKey
    ) {
        return new Submission(userId, problemId, problemVersionId, attemptNo, code, language, idempotencyKey);
    }

    /** Outbox Relay가 JudgeRequested 발행에 성공한 뒤 호출 (PENDING -> QUEUED). */
    public void markQueued() {
        this.status = SubmissionStatus.QUEUED;
    }

    /** Judge Worker가 JudgeRequested를 수신해 Judge0 호출을 시작할 때 호출 (QUEUED/RETRY_WAIT -> RUNNING). */
    public void markRunning() {
        this.status = SubmissionStatus.RUNNING;
    }

    /**
     * Judge0 응답/Kafka/저장 단계에서 일시적 시스템 오류가 나서 자동 재시도로 넘어갈 때 호출.
     * 재시도 소진 여부는 Application 계층(재시도 정책)이 판단해서 소진 시 markFailed를 대신 호출한다.
     */
    public void markRetryWait() {
        this.status = SubmissionStatus.RETRY_WAIT;
        this.retryCount++;
    }

    /**
     * 채점 완료 처리 — result만 채워지고 failureCode는 비운다.
     */
    public void markCompleted(SubmissionResult result, int executionTimeMs, int memoryUsedKb) {
        this.status = SubmissionStatus.COMPLETED;
        this.result = result;
        this.failureCode = null;
        this.executionTimeMs = executionTimeMs;
        this.memoryUsedKb = memoryUsedKb;
        this.judgedAt = Instant.now();
    }

    /**
     * 채점 시스템 자체 실패로 재시도까지 소진됐을 때 호출 — failureCode만 채워지고 result는 비운다.
     */
    public void markFailed(FailureCode failureCode) {
        this.status = SubmissionStatus.FAILED;
        this.failureCode = failureCode;
        this.result = null;
        this.judgedAt = Instant.now();
    }

    /**
     * status가 COMPLETED 또는 FAILED면 true를 반환
     * 해당 제출이 상태가 변할 수 있는 상황인지를 체크.
     */
    public boolean isTerminal() {
        return status == SubmissionStatus.COMPLETED || status == SubmissionStatus.FAILED;
    }
}