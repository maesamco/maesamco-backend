package com.maesamco.content.application.command;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 커리큘럼 수정에 필요한 값입니다.
 *
 * <p>PATCH 요청이므로 전달되지 않은 필드는 {@code null}이며 변경하지 않습니다.</p>
 */
@Getter
@AllArgsConstructor
public class CurriculumUpdateCommand {

    private final String title;
    private final ProgrammingLanguage language;
}
