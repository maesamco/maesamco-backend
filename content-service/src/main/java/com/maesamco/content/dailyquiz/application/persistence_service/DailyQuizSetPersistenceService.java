package com.maesamco.content.dailyquiz.application.persistence_service;

import com.maesamco.content.dailyquiz.application.command.DailyQuizSetCreateCommand;
import com.maesamco.content.dailyquiz.application.result.DailyQuizSetGenerationResult;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttempt;
import com.maesamco.content.dailyquiz.domain.entity.DailyQuizAttemptItem;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptItemRepository;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Daily Quiz 세트와 배정 문항을 하나의 짧은 DB 트랜잭션으로 저장합니다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DailyQuizSetPersistenceService {

    private final DailyQuizAttemptRepository attemptRepository;
    private final DailyQuizAttemptItemRepository attemptItemRepository;

    public DailyQuizSetGenerationResult create(DailyQuizSetCreateCommand command) {
        Objects.requireNonNull(command, "Daily Quiz 세트 생성 요청은 필수입니다.");

        int totalCount = command.questionIds().size();

        DailyQuizAttempt attempt = DailyQuizAttempt.createReady(
                command.userId(),
                command.attemptDate(),
                totalCount
                );

        DailyQuizAttempt savedAttempt = attemptRepository.save(attempt);
        UUID attemptId = savedAttempt.getId();

        for (int index = 0; index < totalCount; index++) {
            UUID questionId = command.questionIds().get(index);
            int questionOrder = index + 1;

            DailyQuizAttemptItem attemptItem = DailyQuizAttemptItem.assign(
                    attemptId,
                    questionId,
                    questionOrder,
                    totalCount
            );

            attemptItemRepository.save(attemptItem);
        }

        return DailyQuizSetGenerationResult.created(
                attemptId,
                totalCount
        );
    }
}
