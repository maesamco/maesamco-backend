package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.CurriculumFinder;
import com.maesamco.content.application.finder.UnitFinder;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.UnitRepository;
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

    @Transactional(rollbackFor = Exception.class)
    public UnitResponse updateUnit(
            UUID unitId,
            UnitUpdateRequest request
    ) {
        Unit unit =
                unitFinder.getById(
                        unitId
                );

        /*
         * 형제 Unit의 displayOrder와 경쟁할 수 있으므로
         * 실제 순서 변경 요청인 경우 부모 Curriculum을 잠근다.
         */
        if (
                request.getDisplayOrder() != null
                        && !request.getDisplayOrder()
                        .equals(
                                unit.getDisplayOrder()
                        )
        ) {
            curriculumFinder.lockById(
                    unit.getCurriculumId()
            );
        }

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
            unit.changeDisplayOrder(
                    request.getDisplayOrder()
            );
        }

        return UnitResponse.from(
                unit
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

        unit.softDelete(
                userId
        );
    }
}
