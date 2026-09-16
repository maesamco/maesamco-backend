package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.Problem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataProblemRepository extends JpaRepository<Problem, UUID> {

    /** 삭제되지 않은 문제 단건 조회 */
    Optional<Problem> findByIdAndDeletedAtIsNull(UUID problemId);

    /** 문제 행을 비관적 쓰기 잠금 상태로 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select problem
            from Problem problem
            where problem.id = :problemId
              and problem.deletedAt is null
            """)
    Optional<Problem> findByIdForUpdate(
            @Param("problemId") UUID problemId
    );
}