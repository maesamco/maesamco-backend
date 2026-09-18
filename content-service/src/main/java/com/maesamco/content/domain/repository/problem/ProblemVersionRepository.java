package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.ProblemVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemVersionRepository {

    ProblemVersion save(ProblemVersion problemVersion);

    Optional<ProblemVersion> findById(UUID problemVersionId);

    void flush();

    Optional<ProblemVersion> findByProblemIdAndVersionNo(
            UUID problemId,
            Integer versionNo
    );

    List<ProblemVersion> findAllByProblemIdOrderByVersionNoDesc(
            UUID problemId
    );
}