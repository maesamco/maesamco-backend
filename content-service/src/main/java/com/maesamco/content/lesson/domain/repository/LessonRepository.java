package com.maesamco.content.lesson.domain.repository;

import com.maesamco.content.lesson.domain.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID>, LessonSearchRepository {

    Optional<Lesson> findByIdAndDeletedAtIsNull(UUID lessonId);
}