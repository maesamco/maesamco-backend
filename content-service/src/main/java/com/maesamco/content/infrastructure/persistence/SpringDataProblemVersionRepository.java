package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.problem.ProblemVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataProblemVersionRepository extends JpaRepository<ProblemVersion, UUID> {

    /** 특정 문제의 특정 버전 조회 */
    Optional<ProblemVersion> findByProblemIdAndVersionNo(UUID problemId, Integer versionNo);

    /** 특정 문제의 전체 버전 이력을 버전 번호 내림차순으로 조회 */
    List<ProblemVersion> findAllByProblemIdOrderByVersionNoDesc(UUID problemId);
}