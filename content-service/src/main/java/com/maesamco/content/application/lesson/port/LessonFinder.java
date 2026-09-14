package com.maesamco.content.application.lesson.port;

import com.maesamco.content.domain.lesson.entity.Lesson;

import java.util.UUID;

/** 레슨 조회 기능을 제공합니다. */
public interface LessonFinder {

    Lesson findLessonById(UUID lessonId);
}