package com.maesamco.content.lesson.presentation.dto.request;

import com.maesamco.content.lesson.domain.enums.ProgrammingLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 레슨 생성 요청 DTO */
@Getter
@NoArgsConstructor
public class LessonCreateRequest {

    @NotNull
    private UUID unitId;

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    @Size(max = 100)
    private String description;

    @NotBlank
    private String content;

    @NotNull
    private ProgrammingLanguage language;
}