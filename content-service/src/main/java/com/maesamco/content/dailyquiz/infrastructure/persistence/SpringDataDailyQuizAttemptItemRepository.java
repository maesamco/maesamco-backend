package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataDailyQuizAttemptItemRepository
        extends JpaRepository<DailyQuizAttemptItem, UUID> {
}
