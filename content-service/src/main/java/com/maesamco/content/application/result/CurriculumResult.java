package com.maesamco.content.application.result;

import com.maesamco.content.domain.entity.Curriculum;
import com.maesamco.content.domain.entity.ProgrammingLanguage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 커리큘럼 조회·생성·수정 결과입니다. Presentation의 응답 DTO와 분리된 Application 전용 타입입니다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CurriculumResult {

    private final UUID id;
    private final ProgrammingLanguage language;
    private final String title;

    public static CurriculumResult from(Curriculum curriculum) {
        return new CurriculumResult(
                curriculum.getId(),
                curriculum.getLanguage(),
                curriculum.getTitle()
        );
    }
}
