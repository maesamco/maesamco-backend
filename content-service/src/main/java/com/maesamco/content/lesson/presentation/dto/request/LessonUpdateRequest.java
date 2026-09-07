package com.maesamco.content.lesson.presentation.dto.request;

import com.maesamco.content.lesson.domain.enums.ProgrammingLanguage;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 레슨 수정 요청 DTO */
@Getter
@NoArgsConstructor
public class LessonUpdateRequest {

    @Size(max = 100)
    private String title;

    @Size(max = 100)
    private String description;

    private String content;

    private ProgrammingLanguage language;

    @Positive
    private Integer displayOrder;
}