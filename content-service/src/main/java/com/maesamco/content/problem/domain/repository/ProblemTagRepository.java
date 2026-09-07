package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.ProblemTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemTagRepository extends JpaRepository<ProblemTag, UUID> {

    /** 문제-태그 연결 존재 여부 조회 */
    boolean existsByProblemIdAndTagId(UUID problemId, UUID tagId);

    /** 문제-태그 연결 단건 조회 */
    Optional<ProblemTag> findByProblemIdAndTagId(UUID problemId, UUID tagId);

    /** 특정 문제의 태그 연결 목록 조회 */
    List<ProblemTag> findAllByProblemId(UUID problemId);
}