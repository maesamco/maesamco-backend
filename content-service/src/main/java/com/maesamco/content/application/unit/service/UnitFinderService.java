package com.maesamco.content.application.unit.service;

import com.maesamco.content.application.unit.port.UnitFinder;
import com.maesamco.content.domain.curriculum.repository.CurriculumRepository;
import com.maesamco.content.domain.unit.entity.Unit;
import com.maesamco.content.domain.unit.repository.UnitRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitFinderService implements UnitFinder {

    private final UnitRepository unitRepository;
    private final CurriculumRepository curriculumRepository;

    /** 삭제되지 않은 유닛과 활성 커리큘럼을 함께 확인합니다. */
    @Override
    @Transactional(readOnly = true)
    public Unit findById(UUID unitId) {
        Unit unit = unitRepository.findById(unitId)
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

        return unit;
    }
}
