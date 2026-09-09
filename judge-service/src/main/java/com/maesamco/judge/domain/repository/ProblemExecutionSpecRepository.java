package com.maesamco.judge.domain.repository;

import com.maesamco.judge.domain.entity.ProblemExecutionSpec;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemExecutionSpecRepository extends JpaRepository<ProblemExecutionSpec, UUID> {

    boolean existsByProblemIdAndProblemVersionId(UUID problemId, UUID problemVersionId);

    Optional<ProblemExecutionSpec> findFirstByProblemIdOrderByPublishedAtDesc(UUID problemId);

    Optional<ProblemExecutionSpec> findByProblemIdAndProblemVersionId(UUID problemId, UUID problemVersionId);
}