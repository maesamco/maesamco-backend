package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProblemProgressRepositoryImpl implements ProblemProgressRepository {

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
    public List<ProblemProgress> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId) {
        return springDataProblemProgressRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Override
    public List<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(UUID userId, ProblemProgressStatus progressStatus) {
        return springDataProblemProgressRepository
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus);
    }

    @Override
    public Page<ProblemProgress> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable) {
        return springDataProblemProgressRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
    }

    @Override
    public Page<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(UUID userId, ProblemProgressStatus progressStatus, Pageable pageable) {
        return springDataProblemProgressRepository
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus, pageable);
    }
}