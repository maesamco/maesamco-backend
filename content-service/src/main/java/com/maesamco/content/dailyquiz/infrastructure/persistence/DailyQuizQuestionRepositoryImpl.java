package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DailyQuizQuestionRepositoryImpl implements DailyQuizQuestionRepository {

    private final SpringDataDailyQuizQuestionRepository springDataRepository;

    @Override
    public DailyQuizQuestion save(DailyQuizQuestion question) {
        return springDataRepository.save(question);
    }

    @Override
    public List<DailyQuizQuestion> findActiveByAnyConcepts(List<String> conceptTags) {
        if (conceptTags == null) {
            throw invalidInput("개념 태그 목록은 필수입니다.");
        }
        if (conceptTags.contains(null)) {
            throw invalidInput("개념 태그는 비어 있을 수 없습니다.");
        }
        if (conceptTags.isEmpty()) {
            return List.of();
        }

        String[] conceptTagArray = conceptTags.toArray(String[]::new);

        // 각 개념에서 전체 슬롯 수 이상의 후보를 유지하면 현재 이분 매칭의
        // 최대 매칭 크기를 보존하면서 문제은행 전체 조회를 피할 수 있습니다.
        int limitPerConcept = conceptTags.size();

        return springDataRepository.findActiveByAnyConceptTags(
                conceptTagArray,
                limitPerConcept
        );
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }
}
