package com.maesamco.content.lesson.presentation.dto.request;

import com.maesamco.content.lesson.domain.enums.ProgrammingLanguage;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LessonUpdateRequest {

    @Size(max = 100)
    private String title;

    @Size(max = 100)
    private String description;

    private String content;

    private ProgrammingLanguage language;

    private Integer displayOrder;
}