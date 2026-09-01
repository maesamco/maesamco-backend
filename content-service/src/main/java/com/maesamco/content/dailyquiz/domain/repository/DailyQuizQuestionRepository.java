package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DailyQuizQuestionRepository extends JpaRepository<DailyQuizQuestion, UUID> {
}
