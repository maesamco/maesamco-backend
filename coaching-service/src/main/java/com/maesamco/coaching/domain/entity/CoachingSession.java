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
 * 대신 (user_id, problem_id)가 IN_PROGRESS인 세션은 항상 최대 1개만 존재하게 강제해서(V4
 * 마이그레이션의 부분 UNIQUE 인덱스), 한 번 COMPLETED된 문제를 나중에 다시 풀 때는 새 세션이
 * 만들어지는 것도 허용한다. submission_id는 "이 세션이 지금 다루고 있는 제출"을 가리키는
 * 값으로, 재시도마다 최신 제출로 갈아탈 수 있다(현재는 힌트 흐름에서 갈아탈 필요가 없어
 * 업데이트 메서드를 아직 추가하지 않았다 — 필요해지면 추가할 것).
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
// ⚠️ 위 uniqueConstraints/indexes는 JPA validate 대상 문서화일 뿐이고, "(user_id, problem_id)가
// IN_PROGRESS일 때만 유일"이라는 부분 UNIQUE 인덱스는 애노테이션으로 표현할 수 없어
// V4 마이그레이션(uk_coaching_sessions_user_problem_in_progress)에만 존재한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CoachingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "submission_id", updatable = false, nullable = false)
    private UUID submissionId;

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
    private CoachingSession(UUID submissionId, UUID userId, UUID problemId) {
        this.submissionId = Validate.requireNonNull(submissionId, "제출 ID");
        this.userId = Validate.requireNonNull(userId, "사용자 ID");
        this.problemId = Validate.requireNonNull(problemId, "문제 ID");
        this.status = CoachingSessionStatus.IN_PROGRESS;
    }

    public static CoachingSession create(UUID submissionId, UUID userId, UUID problemId) {
        return CoachingSession.builder()
                .submissionId(submissionId)
                .userId(userId)
                .problemId(problemId)
                .build();
    }

    /**
     * 역질문 답변까지 완료된 시점에 호출 — 스트릭 반영 기준(서비스 기능 요약 [1]-4절).
     *
     * ⚠️ 낙관적 락 없이 상태를 확인 후 변경한다(check-then-act). 같은 세션에 대해 짧은 시간 안에
     * 두 번 호출되는 경로(예: 클라이언트 재시도)가 생기면 완료 처리가 중복될 수 있다 — 이 메서드를
     * 호출하는 Service/Facade를 만들 때 멱등 처리 여부를 함께 고려할 것.
     *
     * TODO: 역질문 답변 처리 Service/Facade 구현 시 위 동시성 문제(멱등 처리 또는 낙관적 락)
     *       해결 방안 확정하고 이 TODO 제거.
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
