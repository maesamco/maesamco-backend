package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.problem.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ProblemQueryRepository {

    Optional<Problem> findById(UUID problemId);

    Page<Problem> searchProblems(
            ProblemSearchCondition condition,
            Pageable pageable
    );
}