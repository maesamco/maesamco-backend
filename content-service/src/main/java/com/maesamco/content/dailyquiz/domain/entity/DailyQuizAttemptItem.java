package com.maesamco.content.dailyquiz.domain.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "p_daily_quiz_attempt_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyQuizAttemptItem {

    private static final int MAX_RESPONSE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_attempt_id", nullable = false, updatable = false)
    private DailyQuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_quiz_question_id", nullable = false, updatable = false)
    private DailyQuizQuestion question;

    @Column(name = "user_answer", length = MAX_RESPONSE_LENGTH)
    private String userAnswer;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "question_order", nullable = false, updatable = false)
    private int questionOrder;

    @Column(name = "answered_at")
    private Instant answeredAt;

    private DailyQuizAttemptItem(
            DailyQuizAttempt attempt,
            DailyQuizQuestion question,
            int questionOrder
    ) {
        this.attempt = attempt;
        this.question = question;
        this.questionOrder = questionOrder;
    }

    public static DailyQuizAttemptItem assign(
            DailyQuizAttempt attempt,
            DailyQuizQuestion question,
            int questionOrder
    ) {
        DailyQuizAttempt validatedAttempt = requireAttempt(attempt);
        DailyQuizQuestion validatedQuestion = requireQuestion(question);
        int validatedQuestionOrder = requireValidQuestionOrder(
                questionOrder,
                validatedAttempt.getTotalCount()
        );

        return new DailyQuizAttemptItem(
                validatedAttempt,
                validatedQuestion,
                validatedQuestionOrder
        );
    }

    public boolean isAnswered() {
        return this.userAnswer != null;
    }

    private static DailyQuizAttempt requireAttempt(DailyQuizAttempt attempt) {
        if (attempt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "퀴즈 세트: 필수입니다.");
        }
        return attempt;
    }

    private static DailyQuizQuestion requireQuestion(DailyQuizQuestion question) {
        if (question == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "배정 문제: 필수입니다.");
        }
        return question;
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
