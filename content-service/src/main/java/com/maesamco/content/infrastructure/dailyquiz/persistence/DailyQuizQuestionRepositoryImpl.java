package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.QuestionSlot;
import com.maesamco.content.domain.dailyquiz.QuestionSlots;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestionStatus;
import com.maesamco.content.domain.dailyquiz.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DailyQuizQuestionRepositoryImpl implements DailyQuizQuestionRepository {

    private final SpringDataDailyQuizQuestionRepository springDataRepository;

    @Override
    public DailyQuizQuestion save(DailyQuizQuestion question) {
        return springDataRepository.save(question);
    }

    @Override
    public Optional<DailyQuizQuestion> findById(UUID questionId) {
        return springDataRepository.findById(questionId);
    }

    @Override
    public List<DailyQuizQuestion> findActiveByQuestionSlots(QuestionSlots questionSlots) {
        if (questionSlots == null) {
            throw invalidInput("문항 슬롯은 필수입니다.");
        }

        String[] conceptTags = questionSlots.values().stream()
                .map(QuestionSlot::conceptTag)
                .toArray(String[]::new);
        String[] problemTypes = questionSlots.values().stream()
                .map(QuestionSlot::problemType)
                .map(Enum::name)
                .toArray(String[]::new);

        return springDataRepository.findActiveByQuestionSlots(
                conceptTags,
                problemTypes,
                questionSlots.size()
        );
    }

    @Override
    public List<DailyQuizQuestion> findActiveFallbackQuestions() {
        return springDataRepository.findByStatusAndFallbackEligibleTrue(DailyQuizQuestionStatus.ACTIVE);
    }

    @Override
    public List<DailyQuizQuestion> findAllById(Collection<UUID> questionIds) {
        // Spring Data Repository에서 배정된 문제 버전들을 ID로 한 번에 조회
        return springDataRepository.findAllById(questionIds);
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }
}
