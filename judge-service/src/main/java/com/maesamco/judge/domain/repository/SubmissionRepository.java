package com.maesamco.judge.domain.repository;

import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(max(s.attemptNo), 0) from Submission s "
            + "where s.userId = :userId and s.problemId = :problemId")
    int findMaxAttemptNoByUserIdAndProblemId(@Param("userId") UUID userId, @Param("problemId") UUID problemId);

    /** 재시도 스케줄러가 폴링 배치로 쓰는 조회 — 오래된 것부터 batchSize만큼. */
    @Query(value = "SELECT * FROM judge_schema.p_submissions " +
            "WHERE status = :status " +
            "ORDER BY submitted_at ASC " +
            "LIMIT :limit " +
            "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Submission> findByStatusOrderBySubmittedAtAscForUpdateSkipLocked(
            @Param("status") String status, @Param("limit") int limit);

    Page<Submission> findByUserId(UUID userId, Pageable pageable);

    Page<Submission> findByUserIdAndProblemId(UUID userId, UUID problemId, Pageable pageable);
}
