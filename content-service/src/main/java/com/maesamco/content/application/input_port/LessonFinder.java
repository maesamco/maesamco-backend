package com.maesamco.content.application.input_port;

import com.maesamco.content.domain.entity.Lesson;

import java.util.UUID;

/** 레슨 조회 기능을 제공합니다. */
public interface LessonFinder {

    Lesson getById(UUID lessonId);
}