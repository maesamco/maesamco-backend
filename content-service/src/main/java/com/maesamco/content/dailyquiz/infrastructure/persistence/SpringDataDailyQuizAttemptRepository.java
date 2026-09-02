package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataDailyQuizAttemptRepository
        extends JpaRepository<DailyQuizAttempt, UUID> {
}
