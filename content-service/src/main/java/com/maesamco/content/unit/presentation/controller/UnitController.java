package com.maesamco.content.unit.presentation.controller;

import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageableFactory;
import com.maesamco.content.unit.application.service.UnitService;
import com.maesamco.content.unit.presentation.dto.request.UnitCreateRequest;
import com.maesamco.content.unit.presentation.dto.request.UnitUpdateRequest;
import com.maesamco.content.unit.presentation.dto.response.UnitCreateResponse;
import com.maesamco.content.unit.presentation.dto.response.UnitResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 유닛 생성, 조회, 수정, 삭제를 위한 API를 제공합니다.
 *
 * <p>유닛 생성, 단건 조회, 특정 커리큘럼 유닛 목록 조회, 수정, 삭제는
 * {@link UnitService}에 위임합니다.</p>
 *
 * <p>모든 정상 응답은 {@link SuccessResponse}로 감싸 반환하며,
 * 목록 조회 결과는 {@link PageResponse}를 사용해 페이징 정보를 함께 제공합니다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/units")
public class UnitController {

    /** 유닛 생성, 조회, 수정, 삭제 비즈니스 로직을 담당하는 서비스입니다. */
    private final UnitService unitService;

    /**
     * 새로운 유닛을 생성합니다.
     *
     * <p>요청 본문의 유닛 생성 정보를 검증한 뒤,
     * 유닛 생성 로직을 {@link UnitService}에 위임합니다.</p>
     *
     * @param request 유닛 생성 요청 정보
     * @return 생성된 유닛 정보를 포함한 성공 응답
     */
    @PostMapping
    public ResponseEntity<SuccessResponse<UnitCreateResponse>> createUnit(
            @Valid @RequestBody UnitCreateRequest request
    ) {
        UnitCreateResponse response = unitService.createUnit(request);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 유닛 ID를 기준으로 단일 유닛을 조회합니다.
     *
     * <p>유닛 ID를 기준으로 유닛을 조회하고,
     * 조회 결과를 {@link UnitResponse}로 반환합니다.</p>
     *
     * @param unitId 조회할 유닛의 고유 ID
     * @return 조회된 유닛 정보를 포함한 성공 응답
     */
    @GetMapping("/{unitId}")
    public ResponseEntity<SuccessResponse<UnitResponse>> getUnit(
            @PathVariable UUID unitId
    ) {
        UnitResponse response = unitService.getUnit(unitId);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 특정 커리큘럼에 속한 유닛 목록을 조회합니다.
     *
     * <p>커리큘럼 ID를 기준으로 삭제되지 않은 유닛 목록을 조회하며,
     * 표시 순서(displayOrder)를 기준으로 오름차순 정렬합니다.</p>
     *
     * <p>조회 결과는 {@link PageResponse}로 변환하여
     * 유닛 목록과 페이징 정보를 함께 반환합니다.</p>
     *
     * @param curriculumId 조회할 커리큘럼의 고유 ID
     * @param page 조회할 페이지 번호
     * @param size 한 페이지에 조회할 유닛 개수
     * @return 특정 커리큘럼의 페이징된 유닛 목록
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<UnitResponse>>> getUnits(
            @RequestParam UUID curriculumId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable = PageableFactory.of(page, size, null, null);

        PageResponse<UnitResponse> response = unitService.searchUnits(
                curriculumId,
                pageable
        );

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 유닛의 정보를 수정합니다.
     *
     * <p>유닛 ID로 수정 대상을 식별하고,
     * 요청 본문에 전달된 수정 정보를 반영합니다.</p>
     *
     * <p>수정 요청에 포함된 값만 변경합니다.</p>
     *
     * @param unitId 수정할 유닛의 고유 ID
     * @param request 유닛 수정 요청 정보
     * @return 수정된 유닛 정보를 포함한 성공 응답
     */
    @PatchMapping("/{unitId}")
    public ResponseEntity<SuccessResponse<UnitResponse>> updateUnit(
            @PathVariable UUID unitId,
            @Valid @RequestBody UnitUpdateRequest request
    ) {
        UnitResponse response = unitService.updateUnit(unitId, request);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 유닛을 삭제합니다.
     *
     * <p>유닛 ID를 기준으로 삭제 대상 유닛을 식별한 뒤
     * 유닛 삭제 로직을 {@link UnitService}에 위임합니다.</p>
     *
     * <p>삭제 시 실제 데이터를 제거하지 않고,
     * 삭제 시각과 삭제 사용자 정보를 기록하는 Soft Delete 방식으로 처리합니다.</p>
     *
     * @param unitId 삭제할 유닛의 고유 ID
     * @param userId 삭제를 요청한 사용자의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @DeleteMapping("/{unitId}")
    public ResponseEntity<SuccessResponse<Void>> deleteUnit(
            @PathVariable UUID unitId,
            @AuthenticationPrincipal UUID userId
    ) {
        unitService.deleteUnit(unitId, userId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}