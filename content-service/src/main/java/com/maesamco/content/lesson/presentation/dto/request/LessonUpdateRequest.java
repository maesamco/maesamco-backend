package com.maesamco.content.lesson.presentation.dto.request;

import com.maesamco.content.lesson.domain.enums.ProgrammingLanguage;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 레슨 수정 요청 DTO */
@Getter
@NoArgsConstructor
public class LessonUpdateRequest {

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "레슨 제목은 공백일 수 없습니다."
    )
    @Size(
            max = 100,
            message = "레슨 제목은 100자 이하여야 합니다."
    )
    private String title;

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "레슨 설명은 공백일 수 없습니다."
    )
    @Size(
            max = 100,
            message = "레슨 설명은 100자 이하여야 합니다."
    )
    private String description;

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "레슨 내용은 공백일 수 없습니다."
    )
    private String content;

    private ProgrammingLanguage language;

    @Positive(message = "표시 순서는 양수여야 합니다.")
    private Integer displayOrder;
}
