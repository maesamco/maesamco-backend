package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Unit;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataUnitRepository
        extends JpaRepository<Unit, UUID> {

    Optional<Unit> findByIdAndDeletedAtIsNull(
            UUID unitId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select unit
            from Unit unit
            where unit.id = :unitId
              and unit.deletedAt is null
            """)
    Optional<Unit> findByIdForUpdate(
            @Param("unitId")
            UUID unitId
    );

    @Query("""
            select coalesce(max(unit.displayOrder), 0)
            from Unit unit
            where unit.curriculumId = :curriculumId
              and unit.deletedAt is null
            """)
    int findMaxDisplayOrderByCurriculumId(
            @Param("curriculumId")
            UUID curriculumId
    );

    Page<Unit>
    findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            UUID curriculumId,
            Pageable pageable
    );

    List<Unit>
    findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            UUID curriculumId
    );
}
