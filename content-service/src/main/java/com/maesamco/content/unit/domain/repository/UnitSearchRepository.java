package com.maesamco.content.unit.domain.repository;

import com.maesamco.content.unit.domain.entity.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UnitSearchRepository {

    /** 특정 커리큘럼의 삭제되지 않은 유닛 목록 조회 */
    Page<Unit> searchUnits(UUID curriculumId, Pageable pageable);
}