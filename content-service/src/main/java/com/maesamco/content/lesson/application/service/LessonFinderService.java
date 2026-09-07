package com.maesamco.content.lesson.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.lesson.application.port.LessonFinder;
import com.maesamco.content.lesson.domain.entity.Lesson;
import com.maesamco.content.lesson.domain.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 레슨 조회 기능을 구현합니다. */
@Service
@RequiredArgsConstructor
public class LessonFinderService implements LessonFinder {

    private final LessonRepository lessonRepository;

    @Override
    public Lesson findLessonById(UUID lessonId) {
        return lessonRepository.findByIdAndDeletedAtIsNull(lessonId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.LESSON_NOT_FOUND)
                );
    }
}