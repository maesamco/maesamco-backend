package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.CurriculumFinder;
import com.maesamco.content.application.finder.UnitFinder;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.UnitRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.request.UnitCreateRequest;
import com.maesamco.content.presentation.request.UnitUpdateRequest;
import com.maesamco.content.presentation.response.UnitCreateResponse;
import com.maesamco.content.presentation.response.UnitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 유닛 생성, 조회, 수정, 삭제를 담당하는 서비스 */
@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;

    private final UnitFinder unitFinder;

    private final CurriculumFinder curriculumFinder;

    @Transactional(rollbackFor = Exception.class)
    public UnitCreateResponse createUnit(
            UnitCreateRequest request
    ) {
        /*
         * 동일 Curriculum 아래에서 동시에 Unit을 생성하면
         * 같은 displayOrder가 계산될 수 있으므로 부모를 먼저 잠근다.
         */
        curriculumFinder.lockById(
                request.getCurriculumId()
        );

        int displayOrder =
                Math.addExact(
                        unitRepository
                                .findMaxDisplayOrderByCurriculumId(
                                        request.getCurriculumId()
                                ),
                        1
                );

        Unit unit =
                Unit.create(
                        request.getCurriculumId(),
                        request.getTitle(),
                        request.getLanguage(),
                        displayOrder
                );

        Unit savedUnit =
                unitRepository.save(
                        unit
                );

        return UnitCreateResponse.from(
                savedUnit
        );
    }

    @Transactional(readOnly = true)
    public UnitResponse getUnit(
            UUID unitId
    ) {
        Unit unit =
                unitFinder.getById(
                        unitId
                );

        return UnitResponse.from(
                unit
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<UnitResponse> searchUnits(
            UUID curriculumId,
            Pageable pageable
    ) {
        curriculumFinder.getById(
                curriculumId
        );

        Page<Unit> units =
                unitRepository.searchUnits(
                        curriculumId,
                        pageable
                );

        return PageResponse.from(
                units,
                UnitResponse::from
        );
    }

    /**
     * 학습자용 유닛 단건 조회입니다(#359).
     * 유닛 또는 상위 커리큘럼이 공개(PUBLISHED)되지 않았으면 삭제된 것과 같이 NOT_FOUND로 응답합니다.
     */
    @Transactional(readOnly = true)
    public UnitResponse getUnitForUser(
            UUID unitId
    ) {
        Unit unit =
                unitFinder.getPublishedById(
                        unitId
                );

        return UnitResponse.from(
                unit
        );
    }

    /**
     * 학습자용 유닛 목록 조회입니다(#359).
     * 상위 커리큘럼이 공개 상태일 때만 조회하며, 공개(PUBLISHED)된 유닛만 반환합니다.
     */
    @Transactional(readOnly = true)
    public PageResponse<UnitResponse> searchUnitsForUser(
            UUID curriculumId,
            Pageable pageable
    ) {
        curriculumFinder.getPublishedById(
                curriculumId
        );

        Page<Unit> units =
                unitRepository.searchPublishedUnits(
                        curriculumId,
                        pageable
                );

        return PageResponse.from(
                units,
                UnitResponse::from
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public UnitResponse updateUnit(
            UUID unitId,
            UnitUpdateRequest request
    ) {
        Unit unit =
                unitFinder.getById(
                        unitId
                );

        if (request.getTitle() != null) {
            unit.changeTitle(
                    request.getTitle()
            );
        }

        if (request.getLanguage() != null) {
            unit.changeLanguage(
                    request.getLanguage()
            );
        }

        if (
                request.getDisplayOrder() != null
                        && !request.getDisplayOrder()
                        .equals(
                                unit.getDisplayOrder()
                        )
        ) {
            moveUnit(
                    unit,
                    request.getDisplayOrder()
            );
        }

        return UnitResponse.from(
                unit
        );
    }

    /**
     * Unit을 같은 Curriculum 안의 목표 자리로 옮기고, 형제 Unit을 1..N으로 다시 정렬합니다(#324).
     *
     * <p>displayOrder는 "옮겨 갈 자리"로 해석합니다. 다른 형제가 쓰고 있는 번호로 옮기면
     * 그 사이의 형제들이 한 칸씩 밀리거나 당겨집니다.</p>
     *
     * <ul>
     *     <li>부모 Curriculum을 먼저 잠가 같은 Curriculum의 생성·순서 변경을 직렬화합니다.</li>
     *     <li>목표 자리가 1..(활성 형제 수)를 벗어나면 UNIT_DISPLAY_ORDER_OUT_OF_RANGE로 거절합니다.
     *     트랜잭션이 롤백되므로 같은 요청의 다른 필드 변경도 반영되지 않습니다.</li>
     *     <li>잠금을 기다리는 사이 Unit이 삭제됐으면 UNIT_NOT_FOUND로 거절합니다.</li>
     * </ul>
     */
    private void moveUnit(
            Unit unit,
            int position
    ) {
        curriculumFinder.lockById(
                unit.getCurriculumId()
        );

        List<Unit> siblings =
                unitRepository.findActiveSiblings(
                        unit.getCurriculumId()
                );

        boolean stillActive =
                siblings.stream()
                        .anyMatch(
                                sibling -> sibling.getId()
                                        .equals(unit.getId())
                        );

        if (!stillActive) {
            throw new BusinessException(
                    ErrorCode.UNIT_NOT_FOUND
            );
        }

        if (!SiblingDisplayOrders.isInRange(position, siblings.size())) {
            throw new BusinessException(
                    ErrorCode.UNIT_DISPLAY_ORDER_OUT_OF_RANGE
            );
        }

        unitRepository.reorder(
                SiblingDisplayOrders.moveTo(
                        siblings,
                        Unit::getId,
                        unit.getId(),
                        position
                )
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUnit(
            UUID unitId,
            UUID userId
    ) {
        Unit unit =
                unitFinder.getById(
                        unitId
                );

        // 생성·순서 변경과 같은 부모 락으로 삭제 및 번호 압축을 직렬화한다.
        curriculumFinder.lockById(unit.getCurriculumId());
        List<Unit> siblings = unitRepository.findActiveSiblings(unit.getCurriculumId());
        if (siblings.stream().noneMatch(sibling -> sibling.getId().equals(unitId))) {
            throw new BusinessException(ErrorCode.UNIT_NOT_FOUND);
        }

        unit.softDelete(
                userId
        );
        unitRepository.reorder(siblings.stream()
                .filter(sibling -> !sibling.getId().equals(unitId))
                .toList());
    }

    /**
     * 유닛을 학습자에게 공개합니다(#359). 이미 공개 상태면 그대로 둡니다.
     * 상위 커리큘럼이 비공개면 공개해도 학습자에게는 보이지 않습니다.
     */
    @Transactional(rollbackFor = Exception.class)
    public UnitResponse publishUnit(
            UUID unitId
    ) {
        Unit unit =
                unitFinder.getById(
                        unitId
                );

        unit.publish();

        return UnitResponse.from(
                unit
        );
    }

    /**
     * 유닛을 학습자 조회에서 내립니다(#359). 이미 비공개 상태면 그대로 둡니다.
     * 하위 레슨의 상태값은 바꾸지 않고 학습자 조회 시점에 함께 숨겨집니다.
     */
    @Transactional(rollbackFor = Exception.class)
    public UnitResponse unpublishUnit(
            UUID unitId
    ) {
        Unit unit =
                unitFinder.getById(
                        unitId
                );

        unit.unpublish();

        return UnitResponse.from(
                unit
        );
    }
}
