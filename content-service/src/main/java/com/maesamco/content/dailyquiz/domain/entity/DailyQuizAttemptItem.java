package com.maesamco.content.dailyquiz.domain.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 일일 퀴즈 세트에 배정된 문제 한 개와 사용자의 답변 결과를 나타냅니다.
 *
 * 배정 시에는 답변·정답 여부·응답 시각이 비어 있으며,
 * 제출이 완료되면 해당 결과가 기록됩니다.
 *
 * 답변 제출은 Repository의 {@code WHERE user_answer IS NULL} 조건부 UPDATE로
 * 한 번만 반영합니다. 중복·동시 제출을 방지하기 위해 엔티티에는
 * 별도의 답변 변경 메서드를 제공하지 않습니다.
 */
@Entity
@Table(
        name = "p_daily_quiz_attempt_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"quiz_attempt_id", "daily_quiz_question_id"}
                ),
                @UniqueConstraint(
                        columnNames = {"quiz_attempt_id", "question_order"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyQuizAttemptItem {

    private static final int MAX_RESPONSE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "quiz_attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "daily_quiz_question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "user_answer", length = MAX_RESPONSE_LENGTH)
    private String userAnswer;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "question_order", nullable = false, updatable = false)
    private int questionOrder;

    @Column(name = "answered_at")
    private Instant answeredAt;

    private DailyQuizAttemptItem(
            UUID attemptId,
            UUID questionId,
            int questionOrder
    ) {
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.questionOrder = questionOrder;
    }

    public static DailyQuizAttemptItem assign(
            UUID attemptId,
            UUID questionId,
            int questionOrder,
            int totalCount
    ) {
        return new DailyQuizAttemptItem(
                requireId(attemptId, "퀴즈 세트 ID"),
                requireId(questionId, "배정 문제 ID"),
                requireValidQuestionOrder(questionOrder, totalCount)
        );
    }

    public boolean isAnswered() {
        return this.userAnswer != null;
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, fieldName + ": 필수입니다.");
        }
        return id;
    }

    private static int requireValidQuestionOrder(int questionOrder, int totalCount) {
        if (questionOrder < 1 || questionOrder > totalCount) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "문항 순서는 1 이상 전체 문제 수 이하여야 합니다."
            );
        }
        return questionOrder;
    }
}
