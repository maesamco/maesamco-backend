package com.maesamco.judge.domain.repository;

import com.maesamco.judge.application.result.SubmissionSummaryResult;
import com.maesamco.judge.domain.entity.Submission;
import com.maesamco.judge.domain.entity.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(max(s.attemptNo), 0) from Submission s "
            + "where s.userId = :userId and s.problemId = :problemId")
    int findMaxAttemptNoByUserIdAndProblemId(@Param("userId") UUID userId, @Param("problemId") UUID problemId);

    /**
     * (userId, problemId) 조합에 대한 PostgreSQL 트랜잭션 범위 advisory lock을 획득한다.
     * attemptNo를 "조회 후 +1"로 산정하기 전에 호출해서 동시 요청 간 경합을 직렬화한다(#287).
     * 이 메서드를 호출한 트랜잭션이 커밋/롤백되면 PostgreSQL이 자동으로 락을 해제하므로
     * 별도의 unlock 호출은 필요 없다.
     */
    @Query(value = "select pg_advisory_xact_lock(hashtext(:userId), hashtext(:problemId))", nativeQuery = true)
    void acquireAttemptNoLock(@Param("userId") String userId, @Param("problemId") String problemId);

    /** 재시도 스케줄러가 폴링 배치로 쓰는 조회 — 오래된 것부터 batchSize만큼. */
    List<Submission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status, Pageable pageable);

    /**
     * QUEUED 정체 복구 스케줄러가 쓰는 조회 — 마지막 갱신이 threshold보다 오래된 것을 오래된 제출부터(#350).
     */
    List<Submission> findByStatusAndUpdatedAtBeforeOrderBySubmittedAtAsc(
            SubmissionStatus status, Instant threshold, Pageable pageable);

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