package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.ContentStatus;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.infrastructure.persistence.support.SpringPageConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CurriculumRepositoryImpl
        implements CurriculumRepository {

    private final SpringDataCurriculumRepository
            springDataCurriculumRepository;

    @Override
    public Curriculum save(
            Curriculum curriculum
    ) {
        return springDataCurriculumRepository
                .save(curriculum);
    }

    @Override
    public Optional<Curriculum> findById(
            UUID curriculumId
    ) {
        return springDataCurriculumRepository
                .findByIdAndDeletedAtIsNull(
                        curriculumId
                );
    }

    @Override
    public Optional<Curriculum> findByIdForUpdate(
            UUID curriculumId
    ) {
        return springDataCurriculumRepository
                .findByIdForUpdate(
                        curriculumId
                );
    }

    @Override
    public PageResult<Curriculum> searchCurriculums(
            PageQuery pageQuery
    ) {
        return SpringPageConverter.toPageResult(
                springDataCurriculumRepository
                        .findByDeletedAtIsNullOrderByIdAsc(
                                SpringPageConverter.toPageable(pageQuery)
                        )
        );
    }

    @Override
    public PageResult<Curriculum> searchPublishedCurriculums(
            PageQuery pageQuery
    ) {
        return SpringPageConverter.toPageResult(
                springDataCurriculumRepository
                        .findByStatusAndDeletedAtIsNullOrderByIdAsc(
                                ContentStatus.PUBLISHED,
                                SpringPageConverter.toPageable(pageQuery)
                        )
        );
    }
}
