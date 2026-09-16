package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizAttemptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataDailyQuizAttemptItemRepository
        extends JpaRepository<DailyQuizAttemptItem, UUID> {

    /**
     * 세트에 배정된 문항 내역을 questionOrder 오름차순으로 조회합니다.
     */
    List<DailyQuizAttemptItem> findAllByAttemptIdOrderByQuestionOrder(UUID attemptId);

    /**
     * 특정 세트에 특정 문제 버전이 배정됐는지 단건 조회합니다.
     */
    Optional<DailyQuizAttemptItem> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);

    /**
     * 아직 제출하지 않은 문항에만 답안과 채점 결과를 기록합니다.
     * 벌크 UPDATE 전에는 보류 중인 변경을 DB에 반영하고, 실행 후에는
     * 영속성 컨텍스트를 비워 이벤트 생성 시 최신 채점 결과를 다시 조회합니다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DailyQuizAttemptItem item
            SET item.userAnswer = :userAnswer,
                item.correct = :correct,
                item.answeredAt = :answeredAt
            WHERE item.attemptId = :attemptId
              AND item.questionId = :questionId
              AND item.userAnswer IS NULL
            """)
    int submitIfUnanswered(
            @Param("attemptId") UUID attemptId,
            @Param("questionId") UUID questionId,
            @Param("userAnswer") String userAnswer,
            @Param("correct") boolean correct,
            @Param("answeredAt") Instant answeredAt
    );

    /**
     * 특정 세트에서 아직 제출하지 않은 문항 수를 조회합니다.
     */
    long countByAttemptIdAndUserAnswerIsNull(UUID attemptId);

    /**
     * 특정 세트에서 정답으로 제출된 문항 수를 조회합니다.
     */
    long countByAttemptIdAndCorrectTrue(UUID attemptId);
}
