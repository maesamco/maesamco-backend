package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataProblemVersionRepository extends JpaRepository<ProblemVersion, UUID> {

    /** 문제 ID와 문제 버전 ID로 문제 버전을 조회합니다. */
    Optional<ProblemVersion> findByProblemIdAndId(UUID problemId, UUID id);

    /** 특정 문제의 특정 버전 조회 */
    Optional<ProblemVersion> findByProblemIdAndVersionNo(UUID problemId, Integer versionNo);

    /** 특정 문제의 전체 버전 이력을 버전 번호 내림차순으로 조회 */
    List<ProblemVersion> findAllByProblemIdOrderByVersionNoDesc(UUID problemId);
}