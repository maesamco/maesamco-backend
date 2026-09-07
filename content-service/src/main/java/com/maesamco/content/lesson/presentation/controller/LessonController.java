package com.maesamco.content.lesson.presentation.controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.lesson.application.service.LessonService;
import com.maesamco.content.lesson.presentation.dto.request.LessonCreateRequest;
import com.maesamco.content.lesson.presentation.dto.request.LessonUpdateRequest;
import com.maesamco.content.lesson.presentation.dto.response.LessonCreateResponse;
import com.maesamco.content.lesson.presentation.dto.response.LessonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 레슨 생성, 조회, 수정, 삭제를 위한 API를 제공합니다.
 *
 * <p>레슨 생성, 단건 조회, 특정 유닛 레슨 목록 조회, 수정, 삭제는
 * {@link LessonService}에 위임합니다.</p>
 *
 * <p>모든 정상 응답은 {@link SuccessResponse}로 감싸 반환하며,
 * 목록 조회 결과는 {@link PageResponse}를 사용해 페이징 정보를 함께 제공합니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lessons")
public class LessonController {

    /** 레슨 생성, 조회, 수정, 삭제 비즈니스 로직을 담당하는 서비스입니다. */
    private final LessonService lessonService;

    /**
     * 새로운 레슨을 생성합니다.
     *
     * <p>요청 본문의 레슨 생성 정보를 검증한 뒤,
     * 레슨 생성 로직을 {@link LessonService}에 위임합니다.</p>
     *
     * @param request 레슨 생성 요청 정보
     * @return 생성된 레슨 정보를 포함한 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<SuccessResponse<LessonCreateResponse>> createLesson(
            @Valid @RequestBody LessonCreateRequest request
    ) {
        LessonCreateResponse response = lessonService.createLesson(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.success(response));
    }

    /**
     * 레슨 ID를 기준으로 단일 레슨을 조회합니다.
     *
     * <p>레슨 ID를 기준으로 레슨을 조회하고,
     * 조회 결과를 {@link LessonResponse}로 반환합니다.</p>
     *
     * @param lessonId 조회할 레슨의 고유 ID
     * @return 조회된 레슨 정보를 포함한 성공 응답
     */
    @GetMapping("/{lessonId}")
    public ResponseEntity<SuccessResponse<LessonResponse>> getLesson(
            @PathVariable UUID lessonId
    ) {
        LessonResponse response = lessonService.getLesson(lessonId);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 특정 유닛에 속한 레슨 목록을 조회합니다.
     *
     * <p>유닛 ID를 기준으로 삭제되지 않은 레슨 목록을 조회하며,
     * 표시 순서(displayOrder)를 기준으로 오름차순 정렬합니다.</p>
     *
     * <p>조회 결과는 {@link PageResponse}로 변환하여
     * 레슨 목록과 페이징 정보를 함께 반환합니다.</p>
     *
     * @param unitId 조회할 유닛의 고유 ID
     * @param page 조회할 페이지 번호
     * @param size 한 페이지에 조회할 레슨 개수
     * @return 특정 유닛의 페이징된 레슨 목록
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<LessonResponse>>> getLessons(
            @RequestParam UUID unitId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        PageResponse<LessonResponse> response = lessonService.searchLessons(
                unitId,
                pageable
        );

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 레슨의 정보를 수정합니다.
     *
     * <p>레슨 ID로 수정 대상을 식별하고,
     * 요청 본문에 전달된 수정 정보를 반영합니다.</p>
     *
     * <p>수정 요청에 포함된 값만 변경합니다.</p>
     *
     * @param lessonId 수정할 레슨의 고유 ID
     * @param request 레슨 수정 요청 정보
     * @return 수정된 레슨 정보를 포함한 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{lessonId}")
    public ResponseEntity<SuccessResponse<LessonResponse>> updateLesson(
            @PathVariable UUID lessonId,
            @Valid @RequestBody LessonUpdateRequest request
    ) {
        LessonResponse response = lessonService.updateLesson(lessonId, request);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 레슨을 삭제합니다.
     *
     * <p>레슨 ID를 기준으로 삭제 대상 레슨을 식별한 뒤
     * 레슨 삭제 로직을 {@link LessonService}에 위임합니다.</p>
     *
     * <p>삭제 시 실제 데이터를 제거하지 않고,
     * 삭제 시각과 삭제 사용자 정보를 기록하는 Soft Delete 방식으로 처리합니다.</p>
     *
     * @param lessonId 삭제할 레슨의 고유 ID
     * @param userId 삭제를 요청한 사용자의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{lessonId}")
    public ResponseEntity<SuccessResponse<Void>> deleteLesson(
            @PathVariable UUID lessonId,
            @AuthenticationPrincipal UUID userId
    ) {
        lessonService.deleteLesson(lessonId, userId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}