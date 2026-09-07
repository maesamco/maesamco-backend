package com.maesamco.content.unit.presentation.dto.response;

import com.maesamco.content.curriculum.domain.enums.ProgrammingLanguage;
import com.maesamco.content.unit.domain.entity.Unit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 유닛 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UnitResponse {

    /** 유닛 식별자 */
    private final UUID id;

    /** 커리큘럼 식별자 */
    private final UUID curriculumId;

    /** 유닛 제목 */
    private final String title;

    /** 프로그래밍 언어 */
    private final ProgrammingLanguage language;

    /** 유닛 표시 순서 */
    private final Integer displayOrder;

    public static UnitResponse from(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getCurriculumId(),
                unit.getTitle(),
                unit.getLanguage(),
                unit.getDisplayOrder()
        );
    }
}