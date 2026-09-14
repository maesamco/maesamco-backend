package com.maesamco.content.lesson.domain.repository;

import com.maesamco.content.lesson.domain.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LessonSearchRepository {

    Page<Lesson> searchLessons(UUID unitId, Pageable pageable);
}