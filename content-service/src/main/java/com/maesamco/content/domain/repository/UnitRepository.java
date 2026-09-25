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

    /**
     * 영속성 컨텍스트에 올라온 Unit을 DB의 최신 상태로 다시 읽습니다(#366 리뷰 P2).
     * 부모 락을 잡은 뒤 호출해, 락을 기다리는 사이 다른 트랜잭션이 커밋한 변경을 덮어쓰지 않도록 합니다.
     * 그 사이 삭제(soft delete)됐으면 UNIT_NOT_FOUND를 던집니다.
     */
    void refresh(Unit unit);
}
