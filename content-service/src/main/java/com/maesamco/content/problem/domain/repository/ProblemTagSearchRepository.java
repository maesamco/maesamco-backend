package com.maesamco.content.problem.domain.repository;

import com.maesamco.content.tag.domain.entity.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProblemTagSearchRepository {

    /** 특정 문제의 태그 목록 조회 */
    Page<Tag> searchTagsByProblemId(UUID problemId, Pageable pageable);
}