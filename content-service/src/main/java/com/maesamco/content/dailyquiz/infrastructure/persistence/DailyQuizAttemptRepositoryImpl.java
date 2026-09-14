package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptStatus.IN_PROGRESS;
import static com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptStatus.READY;

@Repository
@RequiredArgsConstructor
public class DailyQuizAttemptRepositoryImpl implements DailyQuizAttemptRepository {

    private final SpringDataDailyQuizAttemptRepository springDataRepository;

    @Override
    public DailyQuizAttempt save(DailyQuizAttempt attempt) {
        return springDataRepository.save(attempt);
    }

    @Override
    public boolean existsByUserIdAndAttemptDate(UUID userId, LocalDate attemptDate) {
        return springDataRepository.existsByUserIdAndAttemptDate(userId, attemptDate);
    }

    @Override
    public Optional<DailyQuizAttempt> findByUserIdAndAttemptDate(
            UUID userId,
            LocalDate attemptDate
    ) {
        return springDataRepository.findByUserIdAndAttemptDate(userId, attemptDate);
    }

    @Override
    public Optional<DailyQuizAttempt> findByIdForUpdate(UUID attemptId) {
        return springDataRepository.findByIdForUpdate(attemptId);
    }

    @Override
    public int startIfReady(UUID attemptId, Instant startedAt) {
        return springDataRepository.updateStatusAndStartedAtIfStatus(
                attemptId,
                READY,
                IN_PROGRESS,
                startedAt
        );
    }
}
