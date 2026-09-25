package com.maesamco.content.application.finder_service;

import com.maesamco.content.application.finder.LessonFinder;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.repository.LessonRepository;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.UnitRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
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
    public Lesson getById(UUID lessonId) {
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

    @Override
    @Transactional(readOnly = true)
    public Lesson getPublishedById(UUID lessonId) {
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

        Curriculum curriculum = curriculumRepository.findById(unit.getCurriculumId())
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.CURRICULUM_NOT_FOUND
                        )
                );

        // 존재 확인(getById)과 같은 순서로 상위부터 공개 여부를 확인한다.
        // 비공개는 존재 자체를 드러내지 않도록 삭제와 같은 NOT_FOUND로 응답한다.
        if (!curriculum.isPublished()) {
            throw new BusinessException(ErrorCode.CURRICULUM_NOT_FOUND);
        }
        if (!unit.isPublished()) {
            throw new BusinessException(ErrorCode.UNIT_NOT_FOUND);
        }
        if (!lesson.isPublished()) {
            throw new BusinessException(ErrorCode.LESSON_NOT_FOUND);
        }

        return lesson;
    }
}
