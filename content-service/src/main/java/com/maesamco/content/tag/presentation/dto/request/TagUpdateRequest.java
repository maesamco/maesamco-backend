package com.maesamco.content.tag.presentation.dto.request;

import com.maesamco.content.tag.domain.enums.TagAttribute;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 태그 수정 요청 DTO */
@Getter
@NoArgsConstructor
public class TagUpdateRequest {

    @Pattern(
            regexp = "(?s).*\\S.*",
            message = "태그 이름은 공백일 수 없습니다."
    )
    @Size(
            max = 50,
            message = "태그 이름은 50자 이하여야 합니다."
    )
    private String name;

    private TagAttribute attribute;
}
