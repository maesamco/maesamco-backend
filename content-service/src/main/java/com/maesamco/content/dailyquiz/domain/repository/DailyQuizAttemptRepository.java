package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DailyQuizAttemptRepository extends JpaRepository<DailyQuizAttempt, UUID> {
}
