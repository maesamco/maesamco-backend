package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataUnitRepository extends JpaRepository<Unit, UUID> {

    /** 삭제되지 않은 유닛 단건 조회 */
    Optional<Unit> findByIdAndDeletedAtIsNull(UUID unitId);

    /** 특정 커리큘럼에 속한 삭제되지 않은 유닛 개수 조회 */
    long countByCurriculumIdAndDeletedAtIsNull(UUID curriculumId);

    /** 특정 커리큘럼의 삭제되지 않은 유닛 목록을 displayOrder, id 오름차순으로 페이지 조회 */
    Page<Unit> findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            UUID curriculumId,
            Pageable pageable
    );
}