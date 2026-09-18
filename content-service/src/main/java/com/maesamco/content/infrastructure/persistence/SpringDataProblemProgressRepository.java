package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataProblemProgressRepository extends JpaRepository<ProblemProgress, UUID> {

    /** 사용자와 문제를 기준으로 문제 풀이 진행 상태를 조회합니다. */
    Optional<ProblemProgress> findByUserIdAndProblemId(UUID userId, UUID problemId);

    /** 사용자의 전체 문제 풀이 진행 이력을 최근 생성 순으로 조회합니다. */
    List<ProblemProgress> findByUserIdOrderByCreatedDesc(UUID userId);

    /** 사용자와 문제 풀이 상태를 기준으로 진행 이력을 최근 생성 순으로 조회합니다. */
    List<ProblemProgress> findByUserIdAndProgressStatusOrderByCreatedDesc(UUID userId, ProblemProgressStatus progressStatus);
}