package com.maesamco.content.application.input_port;

import com.maesamco.content.domain.entity.Unit;

import java.util.UUID;

public interface UnitFinder {

    /** 삭제되지 않은 유닛 단건 조회 */
    Unit getById(UUID unitId);
}