package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemTag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemTagRepository {

    ProblemTag save(ProblemTag problemTag);

    void delete(ProblemTag problemTag);

    boolean existsByProblemIdAndTagId(
            UUID problemId,
            UUID tagId
    );

    Optional<ProblemTag> findByProblemIdAndTagId(
            UUID problemId,
            UUID tagId
    );

    List<ProblemTag> findAllByProblemId(
            UUID problemId
    );

    void deleteAllByTagId(
            UUID tagId
    );

    /**
     * 문제에 연결된 태그를 페이징 조회합니다.
     *
     * <p>정렬은 구현체가 고정합니다(연결 시각 최신순, 동률은 id 내림차순).
     * 다른 목록 조회와 같은 {@link PageQuery}를 받지만 {@code sortOrders}는 사용하지 않으며,
     * 전달해도 무시됩니다. 페이지 번호와 크기만 반영됩니다.</p>
     */
    PageResult<Tag> searchTagsByProblemId(
            UUID problemId,
            PageQuery pageQuery
    );

    List<Tag> findAllTagsByProblemId(
            UUID problemId
    );

    /**
     * 여러 문제 ID에 연결된 태그 중, 특정 속성(attribute)에 해당하는 태그만
     * 중복 없이 조회합니다(이슈 #291) — "이 레슨이 다루는 개념" 같은
     * 파생값을 계산할 때 사용합니다.
     */
    List<Tag> findDistinctTagsByProblemIdsAndAttribute(
            Collection<UUID> problemIds,
            TagAttribute attribute
    );
}