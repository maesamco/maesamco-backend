package com.maesamco.content.domain.repository;

import com.maesamco.content.domain.entity.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitRepository {

    Unit save(Unit unit);

    Optional<Unit> findById(UUID unitId);

    Optional<Unit> findByIdForUpdate(UUID unitId);

    int findMaxDisplayOrderByCurriculumId(UUID curriculumId);

    Page<Unit> searchUnits(
            UUID curriculumId,
            Pageable pageable
    );

    /**
     * 같은 Curriculum의 활성 Unit을 displayOrder, id 오름차순으로 조회합니다(#324).
     * 순서 변경 시 부모 Curriculum을 잠근 뒤 호출합니다.
     */
    List<Unit> findActiveSiblings(UUID curriculumId);

    /**
     * 주어진 목록 순서대로 displayOrder를 1..N으로 다시 부여합니다(#324).
     * 목록은 같은 Curriculum의 활성 Unit 전체여야 합니다.
     */
    void reorder(List<Unit> unitsInOrder);

    /**
     * 같은 Curriculum의 공개(PUBLISHED) Unit 목록을 조회합니다(#359).
     * 상위 Curriculum의 공개 여부는 호출하는 쪽에서 먼저 확인합니다.
     */
    Page<Unit> searchPublishedUnits(
            UUID curriculumId,
            Pageable pageable
    );
}
