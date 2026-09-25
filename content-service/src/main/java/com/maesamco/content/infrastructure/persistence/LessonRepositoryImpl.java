package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.exception.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import com.maesamco.content.domain.entity.ContentStatus;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LessonRepositoryImpl implements LessonRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final SpringDataLessonRepository springDataLessonRepository;

    @Override
    public Lesson save(Lesson lesson) {
        return springDataLessonRepository
                .save(lesson);
    }

    @Override
    public Optional<Lesson> findById(UUID lessonId) {
        return springDataLessonRepository
                .findByIdAndDeletedAtIsNull(lessonId);
    }

    @Override
    public int findMaxDisplayOrderByUnitId(UUID unitId) {
        return springDataLessonRepository
                .findMaxDisplayOrderByUnitId(unitId);
    }

    @Override
    public Page<Lesson> searchLessons(UUID unitId, Pageable pageable) {
        return springDataLessonRepository
                .findByUnitIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                        unitId,
                        pageable
                );
    }

    @Override
    public List<Lesson> findActiveSiblings(UUID unitId) {
        return springDataLessonRepository
                .findByUnitIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(unitId);
    }

    @Override
    public void reorder(List<Lesson> lessonsInOrder) {
        TwoPhaseDisplayOrderUpdater.reassign(
                lessonsInOrder,
                Lesson::changeDisplayOrder,
                springDataLessonRepository::flush
        );
    }

    @Override
    public Page<Lesson> searchPublishedLessons(UUID unitId, Pageable pageable) {
        return springDataLessonRepository
                .findByUnitIdAndStatusAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                        unitId,
                        ContentStatus.PUBLISHED,
                        pageable
                );
    }

    @Override
    public void refresh(Lesson lesson) {
        try {
            entityManager.refresh(lesson);
        } catch (EntityNotFoundException exception) {
            // BaseEntity의 @SQLRestriction(deleted_at IS NULL) 때문에, 락을 기다리는 사이
            // 다른 트랜잭션이 soft delete를 커밋한 행은 refresh에서 "없는 행"이 된다(#366).
            throw new BusinessException(ErrorCode.LESSON_NOT_FOUND);
        }
    }
}
