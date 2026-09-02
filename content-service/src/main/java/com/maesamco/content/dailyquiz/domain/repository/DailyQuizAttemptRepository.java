package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;

public interface DailyQuizAttemptRepository {

    DailyQuizAttempt save(DailyQuizAttempt attempt);
}
