package com.maesamco.content.unit.domain.repository;

import com.maesamco.content.unit.domain.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID>, UnitSearchRepository {

    /** 삭제되지 않은 유닛 단건 조회 */
    Optional<Unit> findByIdAndDeletedAtIsNull(UUID unitId);
}