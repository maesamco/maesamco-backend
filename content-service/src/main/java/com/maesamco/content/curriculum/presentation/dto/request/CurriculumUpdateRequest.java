package com.maesamco.content.curriculum.presentation.dto.request;

import com.maesamco.content.curriculum.domain.enums.ProgrammingLanguage;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CurriculumUpdateRequest {

    @Size(max = 100, message = "커리큘럼 제목은 100자 이하여야 합니다.")
    private String title;

    private ProgrammingLanguage language;
}