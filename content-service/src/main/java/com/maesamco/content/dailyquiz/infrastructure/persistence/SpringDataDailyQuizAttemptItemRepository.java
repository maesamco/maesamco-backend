package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataDailyQuizAttemptItemRepository
        extends JpaRepository<DailyQuizAttemptItem, UUID> {

    /**
     * 세트에 배정된 문항 내역을 questionOrder 오름차순으로 조회합니다.
     */
    List<DailyQuizAttemptItem> findAllByAttemptIdOrderByQuestionOrder(UUID attemptId);
}
