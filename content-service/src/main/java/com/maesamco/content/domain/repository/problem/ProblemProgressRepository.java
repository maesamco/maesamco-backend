package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemProgressRepository {

    ProblemProgress save(ProblemProgress problemProgress);

    Optional<ProblemProgress> findByUserIdAndProblemId(UUID userId, UUID problemId);

    List<ProblemProgress> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    List<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(UUID userId, ProblemProgressStatus progressStatus);

    Page<ProblemProgress> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    Page<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(UUID userId, ProblemProgressStatus progressStatus, Pageable pageable);
}