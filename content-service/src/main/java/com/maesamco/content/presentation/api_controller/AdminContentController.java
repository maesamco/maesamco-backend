package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.application.result.CurriculumResult;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.security.authorization.RequireAdmin;
import com.maesamco.content.global.util.PageQueryFactory;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.presentation.response.CurriculumResponse;
import com.maesamco.content.presentation.response.LessonResponse;
import com.maesamco.content.presentation.response.UnitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 관리자 학습 콘텐츠 조회 및 공개 상태 전환 API(#359).
 *
 * <p>조회는 공개 상태와 관계없이 삭제되지 않은 콘텐츠를 모두 반환하는 기존 서비스 메서드를 사용하고,
 * 학습자 API는 공개 콘텐츠만 반환하는 ...ForUser 메서드를 사용합니다.</p>
 */
@RestController
@RequiredArgsConstructor
public class AdminContentController implements AdminContentApiDocs {

    private final CurriculumService curriculumService;

    private final UnitService unitService;

    private final LessonService lessonService;

    // ===================== Curriculum =====================

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<PageResponse<CurriculumResponse>>> getCurriculums(Integer page, Integer size) {
        PageQuery pageQuery = PageQueryFactory.of(page, size, null, null);

        PageResult<CurriculumResult> result = curriculumService.searchCurriculums(pageQuery);

        return ResponseEntity.ok(
                SuccessResponse.success(PageResponse.from(result, CurriculumResponse::from))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<CurriculumResponse>> getCurriculum(UUID curriculumId) {
        return ResponseEntity.ok(
                SuccessResponse.success(CurriculumResponse.from(curriculumService.getCurriculum(curriculumId)))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<CurriculumResponse>> publishCurriculum(UUID curriculumId) {
        return ResponseEntity.ok(
                SuccessResponse.success(CurriculumResponse.from(curriculumService.publishCurriculum(curriculumId)))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<CurriculumResponse>> unpublishCurriculum(UUID curriculumId) {
        return ResponseEntity.ok(
                SuccessResponse.success(CurriculumResponse.from(curriculumService.unpublishCurriculum(curriculumId)))
        );
    }

    // ===================== Unit =====================

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<PageResponse<UnitResponse>>> getUnits(
            UUID curriculumId,
            Integer page,
            Integer size
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        return ResponseEntity.ok(
                SuccessResponse.success(unitService.searchUnits(curriculumId, pageable))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<UnitResponse>> getUnit(UUID unitId) {
        return ResponseEntity.ok(
                SuccessResponse.success(unitService.getUnit(unitId))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<UnitResponse>> publishUnit(UUID unitId) {
        return ResponseEntity.ok(
                SuccessResponse.success(unitService.publishUnit(unitId))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<UnitResponse>> unpublishUnit(UUID unitId) {
        return ResponseEntity.ok(
                SuccessResponse.success(unitService.unpublishUnit(unitId))
        );
    }

    // ===================== Lesson =====================

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<PageResponse<LessonResponse>>> getLessons(
            UUID unitId,
            Integer page,
            Integer size
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        return ResponseEntity.ok(
                SuccessResponse.success(lessonService.searchLessons(unitId, pageable))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<LessonResponse>> getLesson(UUID lessonId) {
        return ResponseEntity.ok(
                SuccessResponse.success(lessonService.getLesson(lessonId))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<LessonResponse>> publishLesson(UUID lessonId) {
        return ResponseEntity.ok(
                SuccessResponse.success(lessonService.publishLesson(lessonId))
        );
    }

    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<LessonResponse>> unpublishLesson(UUID lessonId) {
        return ResponseEntity.ok(
                SuccessResponse.success(lessonService.unpublishLesson(lessonId))
        );
    }
}
