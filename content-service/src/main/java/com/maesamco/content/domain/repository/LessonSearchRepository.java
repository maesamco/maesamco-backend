package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LessonSearchRepository {

    Page<Lesson> searchLessons(UUID unitId, Pageable pageable);
}