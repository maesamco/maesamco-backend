package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface CurriculumRepository {

    Curriculum save(
            Curriculum curriculum
    );

    Optional<Curriculum> findById(
            UUID curriculumId
    );

    Optional<Curriculum> findByIdForUpdate(
            UUID curriculumId
    );

    PageResult<Curriculum> searchCurriculums(
            PageQuery pageQuery
    );
}
