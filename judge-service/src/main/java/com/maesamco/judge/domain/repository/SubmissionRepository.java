package com.maesamco.judge.domain.repository;

import com.maesamco.judge.application.result.SubmissionSummaryResult;
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
    List<Submission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status, Pageable pageable);

    @Query(value = "select new com.maesamco.judge.application.result.SubmissionSummaryResult("
            + "s.id, s.problemId, s.attemptNo, s.status, s.result, s.submittedAt) "
            +"from Submission s where s.userId = :userId",
    countQuery = "select count(s) from Submission s where s.userId = :userId")
    Page<SubmissionSummaryResult> findSummariesByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = "select new com.maesamco.judge.application.result.SubmissionSummaryResult("
            + "s.id, s.problemId, s.attemptNo, s.status, s.result, s.submittedAt) "
            + "from Submission s where s.userId = :userId and s.problemId = :problemId",
            countQuery = "select count(s) from Submission s where s.userId = :userId and s.problemId = :problemId")
    Page<SubmissionSummaryResult> findSummariesByUserIdAndProblemId(
            @Param("userId") UUID userId, @Param("problemId") UUID problemId, Pageable pageable);

}
