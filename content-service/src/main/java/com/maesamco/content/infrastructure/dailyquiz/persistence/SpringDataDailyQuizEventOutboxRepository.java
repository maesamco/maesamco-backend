package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataDailyQuizEventOutboxRepository extends JpaRepository<DailyQuizEventOutbox, UUID> {
}
