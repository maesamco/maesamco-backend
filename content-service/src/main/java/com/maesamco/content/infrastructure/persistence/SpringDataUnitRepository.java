package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.ContentStatus;
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

    /** 특정 커리큘럼의 삭제되지 않은 특정 상태 유닛 목록을 displayOrder, id 오름차순으로 조회 (#359) */
    Page<Unit>
    findByCurriculumIdAndStatusAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            UUID curriculumId,
            ContentStatus status,
            Pageable pageable
    );
}
