package com.maesamco.content.application.command;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 커리큘럼 생성에 필요한 값입니다. Presentation의 요청 DTO와 분리된 Application 전용 타입입니다. */
@Getter
@AllArgsConstructor
public class CurriculumCreateCommand {

    private final String title;
    private final ProgrammingLanguage language;
}
