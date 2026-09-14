package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyQuizAttemptRepository {

    DailyQuizAttempt save(DailyQuizAttempt attempt);

    boolean existsByUserIdAndAttemptDate(UUID userId, LocalDate attemptDate);

    /**
     * 사용자의 특정 날짜 Daily Quiz 세트를 조회
     * return 조건에 맞는 세트. 존재하지 않으면 빈 Optional
     */
    Optional<DailyQuizAttempt> findByUserIdAndAttemptDate(
            UUID userId,
            LocalDate attemptDate
    );

    /**
     * READY 상태인 세트만 IN_PROGRESS로 전환하고 최초 시작 시각을 기록
     */
    int startIfReady(UUID attemptId, Instant startedAt);
}
