package com.maesamco.content.unit.presentation.dto.request;

import com.maesamco.content.unit.domain.enums.ProgrammingLanguage;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UnitUpdateRequest {

    /** 유닛 제목 */
    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "유닛 제목은 공백일 수 없습니다."
    )
    @Size(
            max = 100,
            message = "유닛 제목은 100자 이하여야 합니다."
    )
    private String title;

    /** 프로그래밍 언어 */
    private ProgrammingLanguage language;

    /** 유닛 표시 순서 */
    private Integer displayOrder;
}
