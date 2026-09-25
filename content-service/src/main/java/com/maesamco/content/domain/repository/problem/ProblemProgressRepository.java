package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemProgressRepository {

    ProblemProgress save(ProblemProgress problemProgress);

    Optional<ProblemProgress> findByUserIdAndProblemId(UUID userId, UUID problemId);

    List<ProblemProgress> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    List<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(UUID userId, ProblemProgressStatus progressStatus);

    PageResult<ProblemProgress> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, PageQuery pageQuery);

    PageResult<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedAtDescIdDesc(UUID userId, ProblemProgressStatus progressStatus, PageQuery pageQuery);
}