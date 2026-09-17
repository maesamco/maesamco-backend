package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.Problem;

import java.util.Optional;
import java.util.UUID;

public interface ProblemCommandRepository {

    Problem save(Problem problem);

    Optional<Problem> findByIdForUpdate(UUID problemId);

    void flush();
}