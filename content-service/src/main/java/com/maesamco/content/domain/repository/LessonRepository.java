package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface LessonRepository {

    Lesson save(Lesson lesson);

    Optional<Lesson> findById(UUID lessonId);

    long countByUnitId(UUID unitId);

    Page<Lesson> searchLessons(UUID unitId, Pageable pageable);
}