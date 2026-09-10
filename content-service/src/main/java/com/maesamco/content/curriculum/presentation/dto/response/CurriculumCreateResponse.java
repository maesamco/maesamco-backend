package com.maesamco.content.curriculum.presentation.dto.response;

import com.maesamco.content.curriculum.domain.entity.Curriculum;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 커리큘럼 생성 응답 DTO */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CurriculumCreateResponse {

    private final UUID id;
    private final String title;

    public static CurriculumCreateResponse from(Curriculum curriculum) {
        return new CurriculumCreateResponse(
                curriculum.getId(),
                curriculum.getTitle()
        );
    }
}