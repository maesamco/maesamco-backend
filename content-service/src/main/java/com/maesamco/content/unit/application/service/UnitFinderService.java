package com.maesamco.content.unit.application.service;

import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.unit.application.port.UnitFinder;
import com.maesamco.content.unit.domain.entity.Unit;
import com.maesamco.content.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitFinderService implements UnitFinder {

    private final UnitRepository unitRepository;

    /** 삭제되지 않은 유닛 단건 조회 */
    @Override
    @Transactional(readOnly = true)
    public Unit findById(UUID unitId) {
        return unitRepository.findByIdAndDeletedAtIsNull(unitId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.UNIT_NOT_FOUND)
                );
    }
}