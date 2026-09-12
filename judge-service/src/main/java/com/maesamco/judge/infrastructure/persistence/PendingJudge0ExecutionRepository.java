package com.maesamco.judge.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingJudge0ExecutionRepository extends JpaRepository<PendingJudge0Execution, UUID> {

    List<PendingJudge0Execution> findAllByOrderByCreatedAtAsc();

    Optional<PendingJudge0Execution> findByJudge0Token(String judge0Token);

    void deleteByJudge0Token(String judge0Token);

    @Query("SELECT p FROM PendingJudge0Execution p WHERE p.createdAt < :threshold")
    List<PendingJudge0Execution> findAllOlderThan(@Param("threshold") Instant threshold);

    List<PendingJudge0Execution> findAllBySubmissionId(UUID submissionId);
}