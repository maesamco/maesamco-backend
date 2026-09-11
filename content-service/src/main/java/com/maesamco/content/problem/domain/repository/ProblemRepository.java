package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.Problem;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * 문제 기본 CRUD 담당 JPA Repository
 */
public interface ProblemRepository
        extends JpaRepository<Problem, UUID>, ProblemSearchRepository {

    @NonNull
    Optional<Problem> findById(@NonNull UUID id);

    /**
     * 문제 행을 잠근 상태로 조회합니다.
     *
     * <p>같은 문제의 테스트케이스 순번을 동시에 계산할 때
     * SELECT MAX + 1 경쟁 조건을 방지하기 위해 사용합니다.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select problem
            from Problem problem
            where problem.id = :problemId
            """)
    Optional<Problem> findByIdForUpdate(
            @Param("problemId") UUID problemId
    );
}
