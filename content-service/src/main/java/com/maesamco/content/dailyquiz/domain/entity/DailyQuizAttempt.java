package com.maesamco.content.dailyquiz.domain.entity;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.MINIMUM_QUESTION_COUNT;
import static com.maesamco.content.dailyquiz.domain.DailyQuizPolicy.TARGET_QUESTION_COUNT;

/**
 * 사용자에게 하루 한 번 제공되는 일일 퀴즈 세트입니다.
 *
 * 세트는 READY → IN_PROGRESS → COMPLETED 순서로 진행됩니다.
 *
 * READY → IN_PROGRESS 전이는 최초 조회 시 Repository의
 * {@code WHERE status = 'READY'} 조건부 UPDATE로 처리합니다.
 * 동시 요청에 의한 시작 시각 덮어쓰기를 방지하기 위해 엔티티에는
 * 별도의 시작 메서드를 제공하지 않습니다.
 */
@Entity
@Table(
        name = "p_daily_quiz_attempts",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"user_id", "attempt_date"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyQuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "attempt_date", nullable = false, updatable = false)
    private LocalDate attemptDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DailyQuizAttemptStatus status;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "total_count", nullable = false, updatable = false)
    private int totalCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private DailyQuizAttempt(
            UUID userId,
            LocalDate attemptDate,
            int totalCount
    ) {
        this.userId = userId;
        this.attemptDate = attemptDate;
        this.status = DailyQuizAttemptStatus.READY;
        this.totalCount = totalCount;
    }

    public static DailyQuizAttempt createReady(
            UUID userId,
            LocalDate attemptDate,
            int totalCount
    ) {
        return new DailyQuizAttempt(
                requireUserId(userId),
                requireAttemptDate(attemptDate),
                requireValidTotalCount(totalCount)
        );
    }

    /**
     * 모든 문항 제출이 끝난 세트를 완료 상태로 전환합니다.
     * 제출 서비스가 세트 행을 잠근 상태에서 호출해야 합니다.
     */
    public void complete(int correctCount, Instant completedAt) {
        validateCanComplete();

        int validatedCorrectCount = requireValidCorrectCount(correctCount, this.totalCount);
        Instant validatedCompletedAt = requireValidCompletedAt(completedAt, this.startedAt);

        this.correctCount = validatedCorrectCount;
        this.completedAt = validatedCompletedAt;
        this.status = DailyQuizAttemptStatus.COMPLETED;
    }

    public boolean isReady() {
        return this.status == DailyQuizAttemptStatus.READY;
    }

    public boolean isInProgress() {
        return this.status == DailyQuizAttemptStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return this.status == DailyQuizAttemptStatus.COMPLETED;
    }

    private void validateCanComplete() {
        if (!isInProgress() || this.startedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_QUIZ_STATUS);
        }
    }

    private static UUID requireUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID: 필수입니다.");
        }
        return userId;
    }

    private static LocalDate requireAttemptDate(LocalDate attemptDate) {
        if (attemptDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "퀴즈 날짜: 필수입니다.");
        }
        return attemptDate;
    }

    private static int requireValidTotalCount(int totalCount) {
        if (totalCount < MINIMUM_QUESTION_COUNT || totalCount > TARGET_QUESTION_COUNT) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "전체 문제 수는 " + MINIMUM_QUESTION_COUNT + "개 이상 "
                            + TARGET_QUESTION_COUNT + "개 이하여야 합니다."
            );
        }
        return totalCount;
    }

    private static int requireValidCorrectCount(int correctCount, int totalCount) {
        if (correctCount < 0 || correctCount > totalCount) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "정답 수는 0 이상 전체 문제 수 이하여야 합니다."
            );
        }
        return correctCount;
    }

    private static Instant requireValidCompletedAt(Instant completedAt, Instant startedAt) {
        if (completedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "완료 시각: 필수입니다.");
        }
        if (completedAt.isBefore(startedAt)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "완료 시각은 시작 시각보다 빠를 수 없습니다."
            );
        }
        return completedAt;
    }
}
