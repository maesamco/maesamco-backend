package com.maesamco.content.application.finder_service;

import com.maesamco.content.application.finder.UnitFinder;
import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.CurriculumRepository;
import com.maesamco.content.domain.repository.UnitRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitFinderService
        implements UnitFinder {

    private final UnitRepository unitRepository;

    private final CurriculumRepository curriculumRepository;

    @Override
    @Transactional(readOnly = true)
    public Unit getById(
            UUID unitId
    ) {
        Unit unit =
                unitRepository.findById(
                                unitId
                        )
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.UNIT_NOT_FOUND
                                )
                        );

        validateCurriculum(
                unit.getCurriculumId()
        );

        return unit;
    }

    @Override
    @Transactional
    public Unit lockById(
            UUID unitId
    ) {
        Unit unit =
                unitRepository
                        .findByIdForUpdate(
                                unitId
                        )
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.UNIT_NOT_FOUND
                                )
                        );

        validateCurriculum(
                unit.getCurriculumId()
        );

        return unit;
    }

    private void validateCurriculum(
            UUID curriculumId
    ) {
        curriculumRepository
                .findById(
                        curriculumId
                )
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.CURRICULUM_NOT_FOUND
                        )
                );
    }
}
