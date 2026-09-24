package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.infrastructure.persistence.support.SpringPageConverter;
import lombok.RequiredArgsConstructor;
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
    public PageResult<ProblemProgress> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, PageQuery pageQuery) {
        return SpringPageConverter.toPageResult(
                springDataProblemProgressRepository
                        .findByUserIdOrderByCreatedAtDescIdDesc(userId, SpringPageConverter.toPageable(pageQuery))
        );
    }

    @Override
    public PageResult<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(UUID userId, ProblemProgressStatus progressStatus, PageQuery pageQuery) {
        return SpringPageConverter.toPageResult(
                springDataProblemProgressRepository
                        .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus, SpringPageConverter.toPageable(pageQuery))
        );
    }
}