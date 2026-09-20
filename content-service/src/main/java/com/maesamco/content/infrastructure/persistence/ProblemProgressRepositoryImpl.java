package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProblemProgressRepositoryImpl
        implements ProblemProgressRepository {

    private final SpringDataProblemProgressRepository springDataProblemProgressRepository;

    @Override
    public ProblemProgress save(ProblemProgress problemProgress) {
        return springDataProblemProgressRepository
                .save(problemProgress);
    }

    @Override
    public Optional<ProblemProgress> findByUserIdAndProblemId(UUID userId, UUID problemId) {
        return springDataProblemProgressRepository
                .findByUserIdAndProblemId(userId, problemId);
    }

    @Override
    public List<ProblemProgress> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        return springDataProblemProgressRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDesc(UUID userId, ProblemProgressStatus progressStatus) {
        return springDataProblemProgressRepository
                .findByUserIdAndProgressStatusOrderByCreatedAtDesc(userId, progressStatus);
    }
}