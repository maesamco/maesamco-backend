package com.maesamco.content.unit.presentation.dto.response;

import com.maesamco.content.unit.domain.entity.Unit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 유닛 생성 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UnitCreateResponse {

    private final UUID id;
    private final String title;

    public static UnitCreateResponse from(Unit unit) {
        return new UnitCreateResponse(
                unit.getId(),
                unit.getTitle()
        );
    }
}