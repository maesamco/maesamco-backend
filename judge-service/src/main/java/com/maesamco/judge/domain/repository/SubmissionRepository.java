package com.maesamco.judge.domain.repository;

import com.maesamco.judge.domain.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(max(s.attemptNo), 0) from Submission s "
            + "where s.userId = :userId and s.problemId = :problemId")
    int findMaxAttemptNoByUserIdAndProblemId(@Param("userId") UUID userId, @Param("problemId") UUID problemId);
}
