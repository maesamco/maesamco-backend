package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;

import java.time.LocalDate;
import java.util.UUID;

public interface DailyQuizAttemptRepository {

    DailyQuizAttempt save(DailyQuizAttempt attempt);

    boolean existsByUserIdAndAttemptDate(UUID userId, LocalDate attemptDate);
}
