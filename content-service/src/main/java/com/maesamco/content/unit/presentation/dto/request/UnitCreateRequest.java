package com.maesamco.content.unit.presentation.dto.request;

import com.maesamco.content.unit.domain.enums.ProgrammingLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class UnitCreateRequest {

    /** 커리큘럼 식별자 */
    @NotNull
    private UUID curriculumId;

    /** 유닛 제목 */
    @NotBlank
    @Size(max = 100)
    private String title;

    /** 프로그래밍 언어 */
    @NotNull
    private ProgrammingLanguage language;

    /** 유닛 표시 순서 */
    @NotNull
    private Integer displayOrder;
}