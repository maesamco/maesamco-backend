package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.Problem;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 문제 기본 CRUD 담당하는 JPA Repository
 * <p>save, findById 같은 기본 CRUD</p>
 */
public interface ProblemRepository extends JpaRepository<Problem, UUID>, ProblemSearchRepository {

    @NonNull Optional<Problem> findById(@NonNull UUID id);
}
