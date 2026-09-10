package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyQuizAttemptItemRepository {

    DailyQuizAttemptItem save(DailyQuizAttemptItem attemptItem);

    /*
     * 세트에 배정된 문항 내역을 노출 순서대로 조회합니다.
     */
    List<DailyQuizAttemptItem> findAllByAttemptIdOrderByQuestionOrder(UUID attemptId);

    /**
     * 특정 세트에 배정된 특정 문제 버전을 단건 조회합니다.
     */
    Optional<DailyQuizAttemptItem> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);
}
