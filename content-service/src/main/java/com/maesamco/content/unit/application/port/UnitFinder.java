package com.maesamco.content.unit.application.port;

import com.maesamco.content.unit.domain.entity.Unit;

import java.util.UUID;

public interface UnitFinder {

    /** 삭제되지 않은 유닛 단건 조회 */
    Unit findById(UUID unitId);
}