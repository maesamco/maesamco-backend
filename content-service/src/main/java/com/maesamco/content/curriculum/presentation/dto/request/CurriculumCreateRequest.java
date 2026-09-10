package com.maesamco.content.curriculum.presentation.dto.request;

import com.maesamco.content.curriculum.domain.enums.ProgrammingLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CurriculumCreateRequest {

    @NotBlank(message = "커리큘럼 제목은 필수입니다.")
    @Size(max = 100, message = "커리큘럼 제목은 100자 이하여야 합니다.")
    private String title;

    @NotNull(message = "프로그래밍 언어는 필수입니다.")
    private ProgrammingLanguage language;
}