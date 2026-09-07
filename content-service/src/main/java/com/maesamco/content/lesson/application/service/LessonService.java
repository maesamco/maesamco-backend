package com.maesamco.content.lesson.application.service;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.lesson.application.port.LessonFinder;
import com.maesamco.content.lesson.domain.entity.Lesson;
import com.maesamco.content.lesson.domain.repository.LessonRepository;
import com.maesamco.content.lesson.presentation.dto.request.LessonCreateRequest;
import com.maesamco.content.lesson.presentation.dto.request.LessonUpdateRequest;
import com.maesamco.content.lesson.presentation.dto.response.LessonCreateResponse;
import com.maesamco.content.lesson.presentation.dto.response.LessonResponse;
import com.maesamco.content.unit.application.port.UnitFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 레슨 생성, 조회, 수정, 삭제를 담당하는 서비스 */
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonFinder lessonFinder;
    private final UnitFinder unitFinder;

    /** 레슨 생성 */
    @Transactional(rollbackFor = Exception.class)
    public LessonCreateResponse createLesson(LessonCreateRequest request) {

        // 상위 유닛 존재 여부 확인
        unitFinder.findById(request.getUnitId());

        int displayOrder = Math.toIntExact(
                lessonRepository.countByUnitId(request.getUnitId()) + 1
        );

        Lesson lesson = Lesson.create(
                request.getUnitId(),
                request.getTitle(),
                request.getDescription(),
                request.getContent(),
                request.getLanguage(),
                displayOrder
        );

        Lesson savedLesson = lessonRepository.save(lesson);

        return LessonCreateResponse.from(savedLesson);
    }

    /** 레슨 단건 조회 */
    @Transactional(readOnly = true)
    public LessonResponse getLesson(UUID lessonId) {

        Lesson lesson = lessonFinder.findLessonById(lessonId);

        return LessonResponse.from(lesson);
    }

    /** 특정 유닛 레슨 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> searchLessons(UUID unitId, Pageable pageable) {

        // 존재하지 않는 유닛에 대한 조회 방지
        unitFinder.findById(unitId);

        Page<Lesson> lessons = lessonRepository.searchLessons(unitId, pageable);

        return PageResponse.from(lessons, LessonResponse::from);
    }

    /** 레슨 수정 */
    @Transactional(rollbackFor = Exception.class)
    public LessonResponse updateLesson(UUID lessonId, LessonUpdateRequest request) {

        Lesson lesson = lessonFinder.findLessonById(lessonId);

        if (request.getTitle() != null)         { lesson.changeTitle(request.getTitle()); }
        if (request.getDescription() != null)   { lesson.changeDescription(request.getDescription()); }
        if (request.getContent() != null)       { lesson.changeContent(request.getContent()); }
        if (request.getLanguage() != null)      { lesson.changeLanguage(request.getLanguage()); }

        return LessonResponse.from(lesson);
    }

    /** 레슨 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteLesson(UUID lessonId, UUID userId) {

        Lesson lesson = lessonFinder.findLessonById(lessonId);

        lesson.softDelete(userId);
    }
}