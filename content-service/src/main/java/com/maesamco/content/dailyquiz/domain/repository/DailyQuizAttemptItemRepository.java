package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;

public interface DailyQuizAttemptItemRepository {

    DailyQuizAttemptItem save(DailyQuizAttemptItem attemptItem);
}
