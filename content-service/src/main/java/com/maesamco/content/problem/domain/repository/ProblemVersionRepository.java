package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.ProblemVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 문제 버전 이력 Repository */
public interface ProblemVersionRepository extends JpaRepository<ProblemVersion, UUID> {

    /** 특정 문제의 특정 버전을 조회한다. */
    Optional<ProblemVersion> findByProblemIdAndVersionNo(UUID problemId, Integer versionNo);

    /** 특정 문제의 전체 버전 이력을 버전 번호 내림차순으로 조회한다. */
    List<ProblemVersion> findAllByProblemIdOrderByVersionNoDesc(UUID problemId);
}