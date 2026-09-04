package com.maesamco.judge.domain.repository;

import com.maesamco.judge.domain.entity.ProblemExecutionSpec;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemExecutionSpecRepository extends JpaRepository<ProblemExecutionSpec, UUID> {

    boolean existsByProblemIdAndProblemVersionId(UUID problemId, UUID problemVersionId);
}