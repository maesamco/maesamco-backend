package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DailyQuizAttemptItemRepository extends JpaRepository<DailyQuizAttemptItem, UUID> {
}
