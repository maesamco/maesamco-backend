package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemProgressRepository {

    ProblemProgress save(ProblemProgress problemProgress);

    Optional<ProblemProgress> findByUserIdAndProblemId(UUID userId, UUID problemId);

    List<ProblemProgress> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDesc(UUID userId, ProblemProgressStatus progressStatus);
}