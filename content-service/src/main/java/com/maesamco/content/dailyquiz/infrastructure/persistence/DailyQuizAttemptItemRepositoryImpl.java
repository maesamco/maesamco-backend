package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DailyQuizAttemptItemRepositoryImpl implements DailyQuizAttemptItemRepository {

    private final SpringDataDailyQuizAttemptItemRepository springDataRepository;

    @Override
    public DailyQuizAttemptItem save(DailyQuizAttemptItem attemptItem) {
        return springDataRepository.save(attemptItem);
    }

    @Override
    public List<DailyQuizAttemptItem> findAllByAttemptIdOrderByQuestionOrder(UUID attemptId) {
        // Spring Data Repository에서 세트별 배정 문항을 노출 순서대로 조회
        return springDataRepository.findAllByAttemptIdOrderByQuestionOrder(attemptId);
    }

    @Override
    public Optional<DailyQuizAttemptItem> findByAttemptIdAndQuestionId(
            UUID attemptId,
            UUID questionId
    ) {
        return springDataRepository.findByAttemptIdAndQuestionId(attemptId, questionId);
    }
}
