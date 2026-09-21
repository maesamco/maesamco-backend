package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.domain.dailyquiz.entity.DailyQuizQuestion;
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
    public List<DailyQuizQuestion> findActiveByAnyConcepts(
            List<String> conceptTags,
            int limitPerConcept
    ) {
        if (conceptTags == null) {
            throw invalidInput("개념 태그 목록은 필수입니다.");
        }
        if (conceptTags.contains(null)) {
            throw invalidInput("개념 태그는 비어 있을 수 없습니다.");
        }
        if (conceptTags.isEmpty()) {
            return List.of();
        }
        if (limitPerConcept <= 0) {
            throw invalidInput("개념별 조회할 문항 수는 1개 이상이어야 합니다.");
        }

        String[] conceptTagArray = conceptTags.toArray(String[]::new);

        return springDataRepository.findActiveByAnyConceptTags(
                conceptTagArray,
                limitPerConcept
        );
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
