package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemProgressRepository {

    ProblemProgress save(ProblemProgress problemProgress);

    Optional<ProblemProgress> findByUserIdAndProblemId(UUID userId, UUID problemId);

    List<ProblemProgress> findByUserIdOrderByCreatedDesc(UUID userId);

    List<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedDesc(UUID userId, ProblemProgressStatus progressStatus);
}