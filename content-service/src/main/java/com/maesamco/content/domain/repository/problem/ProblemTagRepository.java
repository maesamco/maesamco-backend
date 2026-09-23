package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    Page<Tag> searchTagsByProblemId(
            UUID problemId,
            Pageable pageable
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