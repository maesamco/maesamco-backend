package com.maesamco.content.unit.application.service;

import com.maesamco.content.curriculum.application.port.CurriculumFinder;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.unit.application.port.UnitFinder;
import com.maesamco.content.unit.domain.entity.Unit;
import com.maesamco.content.unit.domain.repository.UnitRepository;
import com.maesamco.content.unit.presentation.dto.request.UnitCreateRequest;
import com.maesamco.content.unit.presentation.dto.request.UnitUpdateRequest;
import com.maesamco.content.unit.presentation.dto.response.UnitCreateResponse;
import com.maesamco.content.unit.presentation.dto.response.UnitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 유닛 생성, 조회, 수정, 삭제를 담당하는 서비스 */
@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final UnitFinder unitFinder;
    private final CurriculumFinder curriculumFinder;

    /** 유닛 생성 */
    @Transactional(rollbackFor = Exception.class)
    public UnitCreateResponse createUnit(UnitCreateRequest request) {

        // 상위 커리큘럼 존재 여부 확인
        curriculumFinder.findById(request.getCurriculumId());

        int displayOrder = Math.toIntExact(
                unitRepository.countByCurriculumIdAndDeletedAtIsNull(request.getCurriculumId()) + 1
        );

        Unit unit = Unit.create(
                request.getCurriculumId(),
                request.getTitle(),
                request.getLanguage(),
                displayOrder
        );

        Unit savedUnit = unitRepository.save(unit);

        return UnitCreateResponse.from(savedUnit);
    }

    /** 유닛 단건 조회 */
    @Transactional(readOnly = true)
    public UnitResponse getUnit(UUID unitId) {

        Unit unit = unitFinder.findById(unitId);

        return UnitResponse.from(unit);
    }

    /** 특정 커리큘럼 유닛 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<UnitResponse> searchUnits(UUID curriculumId, Pageable pageable) {

        // 존재하지 않는 커리큘럼에 대한 조회 방지
        curriculumFinder.findById(curriculumId);

        Page<Unit> units = unitRepository.searchUnits(curriculumId, pageable);

        return PageResponse.from(units, UnitResponse::from);
    }

    /** 유닛 수정 */
    @Transactional(rollbackFor = Exception.class)
    public UnitResponse updateUnit(UUID unitId, UnitUpdateRequest request) {

        Unit unit = unitFinder.findById(unitId);

        if (request.getTitle() != null) { unit.changeTitle(request.getTitle()); }
        if (request.getLanguage() != null) { unit.changeLanguage(request.getLanguage()); }
        if (request.getDisplayOrder() != null) { unit.changeDisplayOrder(request.getDisplayOrder()); }

        return UnitResponse.from(unit);
    }

    /** 유닛 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUnit(UUID unitId, UUID userId) {

        Unit unit = unitFinder.findById(unitId);

        unit.softDelete(userId);
    }
}