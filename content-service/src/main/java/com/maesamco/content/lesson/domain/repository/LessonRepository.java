package com.maesamco.content.lesson.domain.repository;

import com.maesamco.content.lesson.domain.entity.Lesson;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID>, LessonSearchRepository {

    @NonNull Optional<Lesson> findById(@NonNull UUID lessonId);

    long countByUnitId(UUID unitId);
}