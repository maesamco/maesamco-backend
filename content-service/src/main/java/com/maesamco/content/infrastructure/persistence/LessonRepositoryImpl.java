package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LessonRepositoryImpl implements LessonRepository {

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
}