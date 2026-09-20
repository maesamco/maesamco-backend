package com.maesamco.content.application.service.finder;

import com.maesamco.content.application.input_port.ProblemProgressFinder;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProblemProgressFinderService implements ProblemProgressFinder {

    private final ProblemProgressRepository problemProgressRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProblemProgress> getByUserIdAndProblemId(UUID userId, UUID problemId) {
        return problemProgressRepository
                .findByUserIdAndProblemId(userId, problemId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemProgress> getByUserId(UUID userId) {
        return problemProgressRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemProgress> getByUserIdAndProgressStatus(UUID userId, ProblemProgressStatus progressStatus) {
        return problemProgressRepository
                .findByUserIdAndProgressStatusOrderByCreatedAtDesc(userId, progressStatus);
    }
}