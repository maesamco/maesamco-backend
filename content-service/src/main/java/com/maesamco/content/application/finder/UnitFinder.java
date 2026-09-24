package com.maesamco.content.application.finder;

import com.maesamco.content.domain.entity.Unit;

import java.util.UUID;

public interface UnitFinder {

    Unit getById(UUID unitId);

    Unit lockById(UUID unitId);
}
