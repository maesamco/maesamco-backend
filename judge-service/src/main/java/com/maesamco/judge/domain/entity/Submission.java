package com.maesamco.judge.domain.entity;

import com.maesamco.judge.global.common.BaseEntity;
import com.maesamco.judge.global.exception.BusinessException;
import com.maesamco.judge.global.exception.ErrorCode;
import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Table(name = "p_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id", "attempt_no"}))
@Getter
@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission extends BaseEntity {

    private static final Set<SubmissionStatus> TERMINAL_STATUSES =
            EnumSet.of(SubmissionStatus.COMPLETED, SubmissionStatus.FAILED);

    private static final int MAX_CODE_BYTES = 100 * 1024; // 100KB
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

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

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Submission(
            UUID userId,
            UUID problemId,
            UUID problemVersionId,
            int attemptNo,
            String code,
            SubmissionLanguage language,
            String idempotencyKey
    ) {
        validate(userId, problemId, problemVersionId, attemptNo, code, language, idempotencyKey);

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
        if (this.status == SubmissionStatus.QUEUED) {
            return;
        }
        transition(SubmissionStatus.QUEUED, EnumSet.of(SubmissionStatus.PENDING));
    }
    
    /** Judge Worker가 JudgeRequested를 수신해 Judge0 호출을 시작할 때 호출 (QUEUED/RETRY_WAIT -> RUNNING). */
    public void markRunning() {
        if (this.status == SubmissionStatus.RUNNING) {
            return;
        }
        transition(SubmissionStatus.RUNNING, EnumSet.of(SubmissionStatus.QUEUED, SubmissionStatus.RETRY_WAIT));
    }

    /**
     * Judge0 응답/Kafka/저장 단계에서 일시적 시스템 오류가 나서 자동 재시도로 넘어갈 때 호출.
     * 재시도 소진 여부는 Application 계층(재시도 정책)이 판단해서 소진 시 markFailed를 대신 호출한다.
     */
    public void markRetryWait() {
        if (this.status == SubmissionStatus.RETRY_WAIT) {
            return; // 같은 이벤트 중복 처리
        }
        transition(SubmissionStatus.RETRY_WAIT, EnumSet.of(SubmissionStatus.RUNNING));
        this.retryCount++;
    }

    /** 채점 완료 처리 — result만 채워지고 failureCode는 비운다. */
    public void markCompleted(SubmissionResult result, int executionTimeMs, int memoryUsedKb) {
        if (this.status == SubmissionStatus.COMPLETED) {
            return; // 중복/지연 이벤트 — 멱등 no-op
        }
        Objects.requireNonNull(result, "result는 null일 수 없습니다.");
        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("executionTimeMs는 음수일 수 없습니다. executionTimeMs=" + executionTimeMs);
        }
        if (memoryUsedKb < 0) {
            throw new IllegalArgumentException("memoryUsedKb는 음수일 수 없습니다. memoryUsedKb=" + memoryUsedKb);
        }

        transition(SubmissionStatus.COMPLETED,
                EnumSet.of(SubmissionStatus.QUEUED, SubmissionStatus.RUNNING, SubmissionStatus.RETRY_WAIT));
        this.result = result;
        this.failureCode = null;
        this.executionTimeMs = executionTimeMs;
        this.memoryUsedKb = memoryUsedKb;
        this.judgedAt = Instant.now();
    }

    /** 채점 시스템 자체 실패로 재시도까지 소진됐을 때 호출 — failureCode만 채워지고 result는 비운다. */
    public void markFailed(FailureCode failureCode) {
        if (this.status == SubmissionStatus.FAILED) {
            return; // 중복/지연 이벤트 — 멱등 no-op
        }
        Objects.requireNonNull(failureCode, "failureCode는 null일 수 없습니다.");
        transition(SubmissionStatus.FAILED,
                EnumSet.of(SubmissionStatus.QUEUED, SubmissionStatus.RUNNING, SubmissionStatus.RETRY_WAIT));
        this.failureCode = failureCode;
        this.result = null;
        this.judgedAt = Instant.now();
    }

    private static void validate(
            UUID userId, UUID problemId, UUID problemVersionId,
            int attemptNo, String code, SubmissionLanguage language, String idempotencyKey
    ) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");
        Objects.requireNonNull(problemId, "problemId는 null일 수 없습니다.");
        Objects.requireNonNull(problemVersionId, "problemVersionId는 null일 수 없습니다.");
        Objects.requireNonNull(language, "language는 null일 수 없습니다.");

        if (attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo는 1 이상이어야 합니다. attemptNo=" + attemptNo);
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code는 비어있을 수 없습니다.");
        }
        int codeBytes = code.getBytes(StandardCharsets.UTF_8).length;
        if (codeBytes > MAX_CODE_BYTES) {
            throw new IllegalArgumentException(
                    "code는 %dKB(UTF-8 기준)를 초과할 수 없습니다. 실제 크기=%dbytes"
                            .formatted(MAX_CODE_BYTES / 1024, codeBytes));
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey는 비어있을 수 없습니다.");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "idempotencyKey는 %d자를 초과할 수 없습니다. 실제 길이=%d"
                            .formatted(MAX_IDEMPOTENCY_KEY_LENGTH, idempotencyKey.length()));
        }
    }

    /**
     * 상태 전이 공통 검증.
     * - 목표 상태가 현재 상태와 같으면(자기 자신으로의 전이) 호출부에서 이미 멱등 처리했다고 가정하고 여기까지 오지 않음.
     * - 터미널 상태(COMPLETED/FAILED)에서는 어떤 전이도 차단한다 — 지연/중복 Kafka 메시지로 인한 역행 방지.
     * - 그 외에는 목표 상태별로 허용된 이전 상태 목록에 있는지 검증한다.
     */
    private void transition(SubmissionStatus target, Set<SubmissionStatus> allowedFrom) {
        if (TERMINAL_STATUSES.contains(this.status)) {
            log.error("[Judge] 터미널 상태에서 전이 시도 submissionId={}, 현재={}, 시도한전이={}",
                    this.id, this.status, target);
            throw new BusinessException(ErrorCode.SUBMISSION_INVALID_STATE_TRANSITION);
        }
        if (!allowedFrom.contains(this.status)) {
            log.error("[Judge] 허용되지 않는 상태 전이 submissionId={}, 현재={}, 시도한전이={}",
                    this.id, this.status, target);
            throw new BusinessException(ErrorCode.SUBMISSION_INVALID_STATE_TRANSITION);
        }
        this.status = target;
    }

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(status);
    }
}