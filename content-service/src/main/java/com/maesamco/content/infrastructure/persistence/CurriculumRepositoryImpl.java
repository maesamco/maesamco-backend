package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.exception.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
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

    @PersistenceContext
    private EntityManager entityManager;

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

    @Override
    public void refresh(Curriculum curriculum) {
        try {
            entityManager.refresh(curriculum);
        } catch (EntityNotFoundException exception) {
            // BaseEntity의 @SQLRestriction(deleted_at IS NULL) 때문에, 락을 기다리는 사이
            // 다른 트랜잭션이 soft delete를 커밋한 행은 refresh에서 "없는 행"이 된다(#366).
            throw new BusinessException(ErrorCode.CURRICULUM_NOT_FOUND);
        }
    }
}
