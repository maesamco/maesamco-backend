package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.problem.domain.entity.ProblemTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemTagRepository
        extends JpaRepository<ProblemTag, UUID>,
        ProblemTagSearchRepository {

    /**
     * 문제와 태그의 연결 존재 여부를 확인합니다.
     */
    boolean existsByProblemIdAndTagId(
            UUID problemId,
            UUID tagId
    );

    /**
     * 문제와 태그의 연결을 단건 조회합니다.
     */
    Optional<ProblemTag> findByProblemIdAndTagId(
            UUID problemId,
            UUID tagId
    );

    /**
     * 특정 문제에 연결된 전체 태그 연결을 조회합니다.
     */
    List<ProblemTag> findAllByProblemId(
            UUID problemId
    );

    /**
     * 삭제되는 태그를 참조하는 모든 문제-태그 연결을 제거합니다.
     *
     * <p>ProblemTag는 이력 보존 대상이 아니므로 Hard Delete합니다.</p>
     */
    void deleteAllByTagId(
            UUID tagId
    );
}
