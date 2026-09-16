package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UnitRepository {

    Unit save(Unit unit);

    Optional<Unit> findById(UUID unitId);

    long countByCurriculumId(UUID curriculumId);

    Page<Unit> searchUnits(UUID curriculumId, Pageable pageable);
}