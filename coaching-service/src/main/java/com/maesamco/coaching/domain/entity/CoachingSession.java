package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
import com.maesamco.coaching.global.util.Validate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 코칭 세션(힌트·설명·역질문·피드백의 상위 엔티티) — 매삼코 DB 테이블 명세 1절.
 *
 * 2026-09-02 확정: "제출 1건당 세션 1건"이 아니라 "같은 문제에 대한 재시도 묶음당 세션 1건"이다
 * — 오답 재제출마다 submission_id는 바뀌지만 코칭 세션(및 힌트 1~4단계 진행)은 이어진다.
 *
 * 2026-09-03 재검토(이슈 #84, V5): 문제당 세션은 상태와 무관하게 평생 최대 1개다. V4까지는
 * IN_PROGRESS인 세션만 유일성을 강제해서 COMPLETED 후 재도전하면 새 세션이 열릴 수 있었는데,
 * 설명·역질문 조회 API 설계 중 "과거 세션과 최신 세션 중 무엇을 반환?"이라는 애매함을 낳는
 * 걸 발견해 되돌렸다 — 이미 코칭이 끝난 문제를 재제출해도 기존(COMPLETED 포함) 세션을 그대로
 * 재사용한다. submission_id는 "이 세션이 지금 다루고 있는 제출"을 가리키는 값으로, 재시도마다
 * advanceToSubmission()으로 최신 제출로 갈아탄다 — 힌트 조회 API가 요청받은 submissionId가
 * 실제로 이 세션의 최신 제출인지 검증하는 데 쓰인다(PR #70 리뷰).
 *
 * 2026-09-03 PR #88 리뷰(용현님 P1) — Hint/Explanation이 findOrCreate()를 공유하게 되면서,
 * 과거 제출(지연된 요청, 이미 지난 정답 제출에 대한 뒤늦은 설명 등록 등)로 들어온 요청이
 * submission_id를 검증 없이 덮어써 최신 제출 이전으로 되돌릴 수 있었다. last_attempt_no(V7,
 * Judge Service SubmissionSnapshot.attemptNo — 문제당 제출 순번, 단조 증가)를 함께 저장해,
 * advanceToSubmission()이 더 큰 attemptNo가 들어올 때만 실제로 갈아타도록 자체 방어한다.
 *
 * BaseEntity 미적용(불변 보존형, 팀 컨벤션 16절) — 코칭 기록은 삭제하지 않고 보존하는 게
 * 명시적 원칙이라 updated_at/by · deleted_at/by가 없다. 생성자는 이미 userId로 식별되므로
 * created_by도 별도로 두지 않는다.
 */
@Entity
@Table(
        name = "p_coaching_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_coaching_sessions_submission",
                columnNames = {"submission_id"}
        ),
        indexes = @Index(name = "idx_coaching_sessions_user", columnList = "user_id")
)
// ⚠️ 위 uniqueConstraints/indexes는 JPA validate 대상 문서화일 뿐이고, "문제당(user_id,
// problem_id) 평생 최대 1개"라는 UNIQUE 인덱스(uk_coaching_sessions_user_problem)는
// V5 마이그레이션에만 존재한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CoachingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // updatable = false 아님 — advanceToSubmission()으로 재시도마다 최신 제출로 갈아탄다(PR
    // #70 리뷰 교차검증 — updatable = false로 뒀으면 이 필드를 바꿔서 save()해도 Hibernate가
    // UPDATE SQL에서 이 컬럼을 통째로 제외해 DB에는 절대 반영되지 않았을 것).
    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    // PR #88 리뷰(용현님 P1) — submission_id가 실제로 최신 제출을 가리키는지 판단할 기준.
    // advanceToSubmission()이 이 값보다 큰 attemptNo가 들어올 때만 submission_id를 갱신한다.
    @Column(name = "last_attempt_no", nullable = false)
    private int lastAttemptNo;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "problem_id", updatable = false, nullable = false)
    private UUID problemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CoachingSessionStatus status;

    /*
     * TODO: created_at/completed_at이 실제로 TIMESTAMPTZ 컬럼으로 생성되는지 검증하는
     * 회귀 테스트가 없다(BaseEntity처럼 information_schema.columns.data_type을 직접
     * 확인하는 테스트, PR #11에서 BaseEntity 쪽에 이미 지적된 것과 같은 성격 — 이 엔티티는
     * BaseEntity를 상속하지 않아 별도로 필요). 누군가 실수로 Instant를 LocalDateTime으로
     * 되돌려도 지금은 CI가 못 잡아낸다. Repository 통합 테스트에 추가할 것.
     */
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder
    private CoachingSession(UUID submissionId, UUID userId, UUID problemId, int attemptNo) {
        this.submissionId = Validate.requireNonNull(submissionId, "제출 ID");
        this.userId = Validate.requireNonNull(userId, "사용자 ID");
        this.problemId = Validate.requireNonNull(problemId, "문제 ID");
        this.lastAttemptNo = attemptNo;
        this.status = CoachingSessionStatus.IN_PROGRESS;
    }

    public static CoachingSession create(UUID submissionId, UUID userId, UUID problemId, int attemptNo) {
        return CoachingSession.builder()
                .submissionId(submissionId)
                .userId(userId)
                .problemId(problemId)
                .attemptNo(attemptNo)
                .build();
    }

    /**
     * 같은 재시도 묶음 안에서 새 제출로 갈아탈 때 호출(PR #70 리뷰, 용현님 P2) —
     * 힌트 조회 API가 "이 submissionId가 지금 이 세션이 다루는 최신 제출이 맞는지"를
     * 검증할 수 있으려면 이 값이 항상 최신으로 유지돼야 한다. 그렇지 않으면 이 세션 안에서
     * 더 예전에 있었던 submissionId로 조회했을 때 그 사이 갈아탄 최신 제출의 힌트가
     * 엉뚱하게 반환될 수 있다(이슈 #84/V5로 세션이 문제당 유일해진 뒤에도, "다른 세션"이
     * 아니라 "같은 세션 내 예전 제출"에 대한 가드로 여전히 유효하다).
     *
     * 2026-09-03 PR #88 리뷰(용현님 P1) — Hint/Explanation이 findOrCreate()를 공유하게
     * 되면서, 과거 제출(지연된 요청, 이미 지난 정답 제출에 대한 뒤늦은 설명 등록 등)이
     * submissionId를 무검증으로 덮어써 최신 제출 이전으로 되돌릴 수 있었다. attemptNo가
     * lastAttemptNo보다 클 때만 실제로 갈아타고, 아니면 아무 것도 하지 않은 채 false를
     * 반환한다 — 과거 제출에 대한 요청 자체는 정상적인 유스케이스(예: 이미 정답을 낸
     * 이전 제출에 대해 뒤늦게 설명을 등록)이므로 예외로 막지 않고 조용히 무시한다.
     * 호출자(CoachingSessionFinder)는 반환값이 true일 때만 save()한다.
     *
     * ⚠️ 낙관적 락 없이 값을 바로 덮어쓴다(PR #70 리뷰). 같은 세션에 서로 다른
     * submissionId로 두 힌트 요청이 짧은 시간 안에 동시에 들어오면, 나중에 flush되는
     * 쪽이 조용히 덮어써서 하나가 유실될 수 있다. 발생 가능성은 낮지만
     * recordOccurrence()와 같은 계열의 문제라 같이 트래킹할 것 — complete()는 이슈 #51의
     * FollowUpAnswerPersistenceService 트랜잭션(UNIQUE(follow_up_question_id) 제약)으로
     * 이미 해결됐지만, 이 메서드는 그런 UNIQUE 가드가 없어 별도로 남아 있다.
     *
     * TODO: 위 동시성 문제 해결 방안(낙관적 락 등) 확정하고 이 TODO 제거.
     */
    public boolean advanceToSubmission(UUID submissionId, int attemptNo) {
        if (attemptNo <= this.lastAttemptNo) {
            return false;
        }
        this.submissionId = Validate.requireNonNull(submissionId, "제출 ID");
        this.lastAttemptNo = attemptNo;
        return true;
    }

    /**
     * 역질문 답변까지 완료된 시점에 호출 — 스트릭 반영 기준(서비스 기능 요약 [1]-4절).
     *
     * 낙관적 락(@Version) 없이 상태를 확인 후 변경한다(check-then-act). 이슈 #51의
     * FollowUpAnswerPersistenceService가 이 메서드 호출을 FollowUpAnswer 저장과 한
     * 트랜잭션으로 묶어서, **같은** 역질문에 대한 동시 답변은 안전하다(FollowUpAnswer의
     * UNIQUE(follow_up_question_id) 제약으로 한쪽이 저장 단계에서 롤백되므로 이 완료
     * 처리까지 도달 못 함).
     *
     * 다만 한 세션에 서로 다른 역질문이 여러 개 쌓일 수 있어서(재도전 시 새 설명 등록,
     * 이슈 #84), **서로 다른** 역질문 두 개를 순차적으로(며칠 뒤라도) 또는 거의 동시에
     * 답하는 경우는 이 UNIQUE 제약으로 안 막힌다 — 이미 COMPLETED인 세션에 다른 역질문의
     * 답변이 들어오면 이 메서드가 예외를 던지는데, 순차 재진입 케이스는
     * FollowUpAnswerPersistenceService.completeSessionIfNeeded()가 이 메서드 호출 자체를
     * 건너뛰어 해소했다(PR #98 자가 리뷰, 용현님 P1). 두 요청이 진짜 거의 동시에 들어와서
     * 둘 다 이 세션을 COMPLETED 이전 상태로 읽는 레이스까지는 여전히 미해결 —
     * advanceToSubmission()과 함께 @Version 도입 시 같이 해결할 것.
     */
    public void complete() {
        if (this.status == CoachingSessionStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.COACHING_SESSION_ALREADY_COMPLETED);
        }
        this.status = CoachingSessionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public boolean isCompleted() {
        return this.status == CoachingSessionStatus.COMPLETED;
    }
}
