package com.maesamco.coaching.domain.entity;

import com.maesamco.coaching.global.exception.BusinessException;
import com.maesamco.coaching.global.exception.ErrorCode;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 제출 1건당 코칭 세션(힌트·설명·역질문·피드백의 상위 엔티티) — 매삼코 DB 테이블 명세 1절.
 *
 * BaseEntity 미적용(불변 보존형, 팀 컨벤션 16절) — 코칭 기록은 삭제하지 않고 보존하는 게
 * 명시적 원칙이라 updated_at/by · deleted_at/by가 없다. 생성자는 이미 userId로 식별되므로
 * created_by도 별도로 두지 않는다.
 */
@Entity
@Table(
        name = "p_coaching_sessions",
        indexes = @Index(name = "idx_coaching_sessions_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CoachingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "submission_id", updatable = false, nullable = false, unique = true)
    private UUID submissionId;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "problem_id", updatable = false, nullable = false)
    private UUID problemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CoachingSessionStatus status;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder
    private CoachingSession(UUID submissionId, UUID userId, UUID problemId) {
        this.submissionId = requireNonNull(submissionId, "submissionId");
        this.userId = requireNonNull(userId, "userId");
        this.problemId = requireNonNull(problemId, "problemId");
        this.status = CoachingSessionStatus.IN_PROGRESS;
    }

    private static UUID requireNonNull(UUID value, String fieldName) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldName + "는 필수입니다.");
        }
        return value;
    }

    public static CoachingSession create(UUID submissionId, UUID userId, UUID problemId) {
        return CoachingSession.builder()
                .submissionId(submissionId)
                .userId(userId)
                .problemId(problemId)
                .build();
    }

    /** 역질문 답변까지 완료된 시점에 호출 — 스트릭 반영 기준(서비스 기능 요약 [1]-4절). */
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
