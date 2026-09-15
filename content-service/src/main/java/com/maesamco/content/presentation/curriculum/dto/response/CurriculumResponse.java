package com.maesamco.content.presentation.curriculum.dto.response;

import com.maesamco.content.domain.curriculum.entity.Curriculum;
import com.maesamco.content.domain.curriculum.enums.ProgrammingLanguage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 커리큘럼 단건 조회 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CurriculumResponse {

    private final UUID id;
    private final ProgrammingLanguage language;
    private final String title;
    private final Integer displayOrder;

    public static CurriculumResponse from(Curriculum curriculum) {
        return new CurriculumResponse(
                curriculum.getId(),
                curriculum.getLanguage(),
                curriculum.getTitle(),
                curriculum.getDisplayOrder()
        );
    }
}