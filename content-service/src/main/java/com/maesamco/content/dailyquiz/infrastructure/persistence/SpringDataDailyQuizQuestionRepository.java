package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataDailyQuizQuestionRepository
        extends JpaRepository<DailyQuizQuestion, UUID> {
}
