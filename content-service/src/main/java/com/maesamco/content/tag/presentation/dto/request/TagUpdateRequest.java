package com.maesamco.content.tag.presentation.dto.request;

import com.maesamco.content.tag.domain.enums.TagAttribute;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 태그 수정 요청 DTO */
@Getter
@NoArgsConstructor
public class TagUpdateRequest {

    @Size(max = 50)
    private String name;

    private TagAttribute attribute;
}