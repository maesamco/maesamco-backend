package com.maesamco.content.dailyquiz.domain.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자가 특정 일일 퀴즈 문제 버전을 신고한 이력을 나타냅니다.
 *
 * 어떤 문제의 어느 버전이 신고됐는지 기록합니다.
 * 신고자와 처리자 ID는 User Service의 사용자 ID입니다.
 */
@Entity
@Table(
        name = "p_daily_quiz_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"daily_quiz_question_id", "reporter_user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class DailyQuizReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_quiz_question_id", nullable = false, updatable = false)
    private DailyQuizQuestion question;

    @Column(name = "reporter_user_id", nullable = false, updatable = false)
    private UUID reporterUserId;

    @Column(name = "reason", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    private DailyQuizReport(
            DailyQuizQuestion question,
            UUID reporterUserId,
            String reason
    ) {
        this.question = question;
        this.reporterUserId = reporterUserId;
        this.reason = reason;
    }

    public static DailyQuizReport create(
            DailyQuizQuestion question,
            UUID reporterUserId,
            String reason
    ) {
        return new DailyQuizReport(
                requireQuestion(question),
                requireId(reporterUserId, "신고자 ID"),
                requireReason(reason)
        );
    }

    /**
     * 미해결 신고 한 건의 처리자와 처리 시각을 함께 기록합니다.
     * 이미 해결된 신고는 최초 처리 정보를 보존하기 위해 변경하지 않습니다.
     *
     * 이미 새 버전이 존재하는 과거 문제에 늦게 들어온 신고를
     * 접수 즉시 자동 해결할 때 사용합니다.
     *
     * 관리자가 새 버전을 생성한 뒤 해당 문제 버전의 미해결 신고 전체를
     * 해결하는 작업은 엔티티를 개별 조회하지 않고 Repository의 벌크 UPDATE로
     * 처리할 예정입니다.
     */
    public void resolveIfUnresolved(UUID resolverId, Instant resolvedAt) {
        if (isResolved()) {
            return;
        }

        UUID validatedResolverId = requireId(resolverId, "처리자 ID");
        Instant validatedResolvedAt = requireResolvedAt(resolvedAt);

        this.resolvedBy = validatedResolverId;
        this.resolvedAt = validatedResolvedAt;
    }

    public boolean isResolved() {
        return this.resolvedAt != null;
    }

    private static DailyQuizQuestion requireQuestion(DailyQuizQuestion question) {
        if (question == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "신고 대상 문제: 필수입니다.");
        }
        return question;
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldName + ": 필수입니다.");
        }
        return id;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "신고 이유: 필수입니다.");
        }
        return reason.strip();
    }

    private static Instant requireResolvedAt(Instant resolvedAt) {
        if (resolvedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "처리 시각: 필수입니다.");
        }
        return resolvedAt;
    }
}
