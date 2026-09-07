package com.maesamco.content.dailyquiz.infrastructure.persistence;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;
import com.maesamco.content.dailyquiz.domain.repository.DailyQuizQuestionRepository;
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
    public List<DailyQuizQuestion> findActiveByAnyConcepts(
            List<String> conceptTags,
            int limitPerConcept
    ) {
        if (conceptTags.isEmpty()) {
            return List.of();
        }

        if (limitPerConcept < 1) {
            throw new IllegalArgumentException(
                    "개념별 후보 제한 수는 1개 이상이어야 합니다."
            );
        }

        String[] conceptTagArray = conceptTags.toArray(String[]::new);

        return springDataRepository.findActiveByAnyConceptTags(
                conceptTagArray,
                limitPerConcept
        );
    }
}
