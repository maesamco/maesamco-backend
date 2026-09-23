package com.maesamco.content.application.finder_service;

import com.maesamco.content.application.finder.ProblemProgressFinder;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProblemProgressFinderService implements ProblemProgressFinder {

    private final ProblemFinderService problemFinderService;

    private final ProblemProgressRepository problemProgressRepository;

    @Override
    public ProblemProgress getByUserIdAndProblemIdOrigin(UUID userId, UUID problemId) {

        Problem problem = problemFinderService.getById(problemId);

        return problemProgressRepository.findByUserIdAndProblemId(userId, problem.getId())
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.PROBLEM_PROGRESS_NOT_FOUND)
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProblemProgress> getByUserIdAndProblemId(UUID userId, UUID problemId) {

        Problem problem = problemFinderService.getById(problemId);

        return problemProgressRepository.findByUserIdAndProblemId(userId, problem.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemProgress> getByUserId(UUID userId) {
        return problemProgressRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemProgress> getByUserIdAndProgressStatus(UUID userId, ProblemProgressStatus progressStatus) {
        return problemProgressRepository
                .findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(userId, progressStatus);
    }
}