package com.maesamco.content.presentation.request;

import com.maesamco.content.domain.entity.ProgrammingLanguage;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UnitUpdateRequest {

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "유닛 제목은 공백일 수 없습니다."
    )
    @Size(
            max = 100,
            message = "유닛 제목은 100자 이하여야 합니다."
    )
    private String title;

    private ProgrammingLanguage language;

    @Positive(
            message = "표시 순서는 양수여야 합니다."
    )
    private Integer displayOrder;
}
