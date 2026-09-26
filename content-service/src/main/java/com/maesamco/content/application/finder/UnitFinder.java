package com.maesamco.content.application.finder;

import com.maesamco.content.domain.entity.Unit;

import java.util.UUID;

public interface UnitFinder {

    Unit getById(UUID unitId);

    Unit lockById(UUID unitId);

    /**
     * 학습자 조회용으로 유닛을 조회합니다(#359).
     * 유닛이 삭제·비공개면 UNIT_NOT_FOUND, 상위 커리큘럼이 삭제·비공개면 CURRICULUM_NOT_FOUND입니다.
     */
    Unit getPublishedById(
            UUID unitId
    );
}
