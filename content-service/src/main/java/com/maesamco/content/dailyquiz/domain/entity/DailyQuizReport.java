package com.maesamco.content.dailyquiz.domain.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "daily_quiz_question_id", nullable = false, updatable = false)
    private UUID questionId;

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
            UUID questionId,
            UUID reporterUserId,
            String reason
    ) {
        this.questionId = questionId;
        this.reporterUserId = reporterUserId;
        this.reason = reason;
    }

    public static DailyQuizReport create(
            UUID questionId,
            UUID reporterUserId,
            String reason
    ) {
        return new DailyQuizReport(
                requireId(questionId, "신고 대상 문제 ID"),
                requireId(reporterUserId, "신고자 ID"),
                requireReason(reason)
        );
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

}
