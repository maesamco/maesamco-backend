package com.maesamco.content.lesson.application.service;

import com.maesamco.content.curriculum.domain.repository.CurriculumRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.lesson.application.port.LessonFinder;
import com.maesamco.content.lesson.domain.entity.Lesson;
import com.maesamco.content.lesson.domain.repository.LessonRepository;
import com.maesamco.content.unit.domain.entity.Unit;
import com.maesamco.content.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 레슨 조회 기능을 구현합니다. */
@Service
@RequiredArgsConstructor
public class LessonFinderService implements LessonFinder {

    private final LessonRepository lessonRepository;
    private final UnitRepository unitRepository;
    private final CurriculumRepository curriculumRepository;

    @Override
    @Transactional(readOnly = true)
    public Lesson findLessonById(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.LESSON_NOT_FOUND
                        )
                );

        Unit unit = unitRepository.findById(lesson.getUnitId())
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.UNIT_NOT_FOUND
                        )
                );

        curriculumRepository.findById(unit.getCurriculumId())
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.CURRICULUM_NOT_FOUND
                        )
                );

        return lesson;
    }
}
