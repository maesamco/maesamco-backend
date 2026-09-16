package com.maesamco.content.domain.repository.problem;

import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.problem.ProblemTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}