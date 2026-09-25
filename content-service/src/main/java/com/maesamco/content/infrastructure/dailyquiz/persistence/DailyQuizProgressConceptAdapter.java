package com.maesamco.content.infrastructure.dailyquiz.persistence;

import com.maesamco.content.application.dailyquiz.port.ProblemProgressConceptPort;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyQuizProgressConceptAdapter implements ProblemProgressConceptPort {

    private final SpringDataDailyQuizProgressConceptRepository repository;

    @Override
    public boolean existsByUserId(UUID userId) {
        return repository.existsByUserId(userId);
    }

    @Override
    public List<String> getWrongConceptTags(UUID userId) {
        return repository.findConceptTagsByStatus(
                userId, ProblemProgressStatus.WRONG, TagAttribute.CONCEPT
        );
    }

    @Override
    public List<String> getCorrectConceptTagsBefore(UUID userId, Instant quizDateStart) {
        return repository.findConceptTagsByStatusAndSolvedBefore(
                userId, ProblemProgressStatus.CORRECT, quizDateStart, TagAttribute.CONCEPT
        );
    }
}
