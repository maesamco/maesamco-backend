package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Unit;
import com.maesamco.content.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UnitRepositoryImpl implements UnitRepository {

    private final SpringDataUnitRepository springDataUnitRepository;

    @Override
    public Unit save(Unit unit) {
        return springDataUnitRepository.save(unit);
    }

    @Override
    public Optional<Unit> findById(UUID unitId) {
        return springDataUnitRepository.findByIdAndDeletedAtIsNull(unitId);
    }

    @Override
    public long countByCurriculumId(UUID curriculumId) {
        return springDataUnitRepository
                .countByCurriculumIdAndDeletedAtIsNull(curriculumId);
    }

    @Override
    public Page<Unit> searchUnits(UUID curriculumId, Pageable pageable) {
        return springDataUnitRepository
                .findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
                        curriculumId,
                        pageable
                );
    }
}